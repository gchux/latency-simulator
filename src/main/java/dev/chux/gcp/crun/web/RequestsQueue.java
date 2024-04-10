package dev.chux.gcp.crun.web;

import java.lang.ref.WeakReference;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;

import java.time.Duration;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.RejectedExecutionException;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletResponse;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.base.Objects;
import com.google.common.collect.Queues;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.FutureCallback;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class RequestsQueue implements GracefulShutdownCallback {
  
  private static final int EXTRA_TASKS = 3;

  private final int minConcurrentRequests;
  private final int maxConcurrentRequests;

  private final CountDownLatch startSignal = new CountDownLatch(1);
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicReference<RestHandler> restHandler = new AtomicReference<>(null);
 
  private final BlockingQueue<Runnable> requestsQueue;
  private final DelayQueue<PendingRequest> pendingQueue;
  private final ThreadPoolExecutor requestsExecutor;
  private final ListeningExecutorService requestsService;

  private final BlockingQueue<UUID> uuidQueue;

  private final Duration maxPendingLatency;

  private WebServer webServer;
  private String webServerKind;

  RequestsQueue(@Value("${app.web.requests.minConcurrent}") Integer minConcurrentRequests,
                @Value("${app.web.requests.maxConcurrent}") Integer maxConcurrentRequests,
                @Value("${app.web.requests.maxPendingLatency}") Long maxPendingLatency) {
                // @Qualifier("app-WebServer") WebServer webServer) {

    this.webServer = null;

    this.minConcurrentRequests = minConcurrentRequests.intValue();
    this.maxConcurrentRequests = maxConcurrentRequests.intValue();
    this.maxPendingLatency = Duration.ofSeconds(maxPendingLatency.longValue());

    this.pendingQueue = new DelayQueue<>();
    this.requestsQueue = Queues.newLinkedBlockingQueue(this.maxConcurrentRequests + EXTRA_TASKS);
    this.requestsExecutor = new ThreadPoolExecutor(this.minConcurrentRequests + EXTRA_TASKS, 
        this.maxConcurrentRequests + EXTRA_TASKS, 5L, TimeUnit.SECONDS, this.requestsQueue);
    this.requestsService = MoreExecutors.listeningDecorator(this.requestsExecutor);

    this.uuidQueue = Queues.newLinkedBlockingQueue(this.maxConcurrentRequests);
  }

  ListenableFuture<RestRequest> submit(final boolean async, final HttpServletRequest request, final HttpServletResponse response) {

    final int queueRemainingCapacity = this.requestsQueue.remainingCapacity();
    final int queueSize = this.requestsQueue.size();
    final int executorActiveCount = this.requestsExecutor.getActiveCount();
    final int executorPoolSize = this.requestsExecutor.getPoolSize();

    System.out.println("Q: " + queueSize + "/" + queueRemainingCapacity + "/" + (this.maxConcurrentRequests + EXTRA_TASKS));
    System.out.println("X: " + executorPoolSize + "/" + executorActiveCount + "/" + this.requestsExecutor.getMaximumPoolSize());

    final long maxPendingLatency = this.maxPendingLatency.toMillis();

    final RestRequest restRequest = new RestRequest(async, request, response);

    System.out.println("submit: " + restRequest);

    try {
      if( !this.startSignal.await(maxPendingLatency, TimeUnit.MILLISECONDS) ) { 
        // wait for restController to be registered
        response.setStatus(504);
        return Futures.immediateCancelledFuture();
      } 
      final PendingRequest pendingRequest = restRequest.preSubmit(maxPendingLatency);
      this.pendingQueue.add(pendingRequest);
      this.webServer.shutDownGracefully(this);
      return this.requestsService.submit(restRequest, restRequest);
    } catch(RejectedExecutionException rejectedEx) {
      System.out.println("rejected: " + restRequest);
    } catch (Exception ex) {
      return Futures.immediateFailedFuture(ex);
    }

    System.out.println("sinking: " + restRequest);

    try {
      restRequest.run();
      return Futures.immediateFuture(restRequest);
    } catch(Exception ex) {
      return Futures.immediateFailedFuture(ex);
    }
  }

  RestHandler getRestHandler() {
    return this.restHandler.get();
  }

  UUID nextUUID() {
    try {
      return this.uuidQueue.take();
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }
    return UUID.randomUUID();
  }

  public Boolean registerRestHandler(final WebServer webServer, final String webServerKind, final RestHandler restHandler) {
    if( this.restHandler.compareAndSet(null, restHandler) ) {
      if( this.started.compareAndSet(false, true) ) {

        this.webServer = webServer;
        this.webServerKind = webServerKind;

        this.requestsExecutor.prestartAllCoreThreads();
        System.out.println("Registered: " + restHandler);
        this.startSignal.countDown();

        this.requestsService.submit(new Runnable() {
          public void run() {
            while( true ) {
              try {
                RequestsQueue.this.uuidQueue.put(UUID.randomUUID());
              } catch(InterruptedException ex) {
                ex.printStackTrace(System.out);  
              }
            }
          }
        });

        // submit Runnable that will remove expired requests
        this.requestsService.submit(new Runnable() {
          public void run() {
            while( true ) {
              try {
                final PendingRequest pendingRequest = RequestsQueue.this.pendingQueue.take();
                final Optional<RestRequest> restRequest = pendingRequest.get();
                if( restRequest.isPresent() && !restRequest.get().isStarted() ) {
                  System.out.println("expiring: " + restRequest);
                  restRequest.get().expire();
                }
              } catch(InterruptedException ex) {
                ex.printStackTrace(System.out);  
              }
            }
          }
        });

        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }
  
  public void shutdownComplete(final GracefulShutdownResult result) {
    System.out.println(result);
  }

  class PendingRequest implements Delayed, Supplier<Optional<RestRequest>> {

    private final long createdAt;
    private final long expiration;

    private final Supplier<Optional<RestRequest>> restRequest;
    private final UUID requestId;

    private PendingRequest(final Supplier<Optional<RestRequest>> restRequest, 
        final UUID requestId, final long maxDelayMillis) {
      this.createdAt = System.currentTimeMillis();
      this.expiration = this.createdAt + maxDelayMillis;
      this.restRequest = restRequest;
      this.requestId = requestId;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.getDelayMillis(), TimeUnit.MILLISECONDS);
    }

    private long getDelayMillis() {
        return this.getDelayMillis(System.currentTimeMillis());
    }

    private long getDelayMillis(final long msReference) {
        return this.expiration - msReference;
    }

    @Override
    public Optional<RestRequest> get() {
      return this.restRequest.get();
    }

    @Override
    public boolean equals(Object that) {
      return (that instanceof PendingRequest) 
        && this.requestId.equals(((PendingRequest) that).requestId);
    }

    @Override
    public int hashCode() {
      return this.requestId.hashCode();
    }
    
    @Override
    public int compareTo(Delayed that) {
      final long msReference = System.currentTimeMillis();
      return Long.compare(this.getDelayMillis(msReference), ((PendingRequest) that).getDelayMillis(msReference));
    }

  }

  class RestRequest implements Runnable, Supplier<UUID> {

    private final UUID requestId;

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private final AtomicBoolean started;
    private final AtomicBoolean expired;

    private final boolean async;

    private final CountDownLatch startSignal = new CountDownLatch(1);

    private final AtomicReference<PendingRequest> pendingRequest;

    private volatile AsyncContext asyncContext;

    private RestRequest(final boolean async, final HttpServletRequest request, final HttpServletResponse response) {
      this.started = new AtomicBoolean(false);
      this.expired = new AtomicBoolean(false);
      this.request = request;
      this.response = response;
      this.async = async;
      this.requestId = RequestsQueue.this.nextUUID();
      this.pendingRequest = new AtomicReference<>(null);
    } 

    PendingRequest preSubmit(final long maxDelayMillis) {
      this.asyncContext = this.async ? this.request.startAsync(request, response) : null;
      final PendingRequest pendingRequest = this.pendingRequest(maxDelayMillis);
      if( this.asyncContext != null ) {
        this.asyncContext.setTimeout(0l);
      }
      this.startSignal.countDown();
      return pendingRequest;
    }

    public void run() {

      Preconditions.checkNotNull(this.pendingRequest.get(), "RestRequest::preSubmit() must be called 1st");

      if( this.isExpired() ) {
        return;
      }

      try {
        this.startSignal.await(); // wait for postSubmit to complete
        if( !this.isExpired() && this.started.compareAndSet(false, true) ) {
          RequestsQueue.this.pendingQueue.remove(this.pendingRequest.get());
          System.out.println("handling: " + this.request);
          RequestsQueue.this.getRestHandler().handle(this.request, this.response);
        }
      } catch(Exception ex) {
        ex.printStackTrace(System.out);
      } finally {
        if( this.isAsync() ) {
          this.asyncContext.complete();
        }
      } 
    }

    @Override
    public UUID get() {
      return this.requestId;
    }

    public Boolean expire() {
      final boolean expired = !this.isStarted() && this.expired.compareAndSet(false, true);

      System.out.println("expired: " + this.request + " | " + this.isStarted() + " | " + this.isExpired());
      
      if( !expired ) { return Boolean.FALSE; }
      
      if( this.isExpired() && this.isAsync() ) {
        this.response.setStatus(504);
        this.asyncContext.complete();
      }  

      RequestsQueue.this.requestsQueue.remove(this);
      
      return Boolean.TRUE;
    }

    public boolean isAsync() {
      return this.async && (this.asyncContext != null);
    }

    public boolean isStarted() {
      return this.started.get();
    }

    public boolean isExpired() {
      return this.expired.get();
    }

    @Override
    public boolean equals(Object that) {
      return (that instanceof RestRequest) 
        && this.request.equals(((RestRequest) that).request);
    }

    @Override
    public int hashCode() {
      return this.request.hashCode();
    }

    @Override
    public String toString() {
      return this.request.toString();
    }

    private PendingRequest pendingRequest(final long maxDelayMillis) {
      PendingRequest pendingRequest = this.pendingRequest.get();
      if( pendingRequest != null ) {
        return pendingRequest;
      }
      pendingRequest = new PendingRequest(new RestRequestSupplier(this), this.get(), maxDelayMillis);
      if( this.pendingRequest.compareAndSet(null, pendingRequest) ) {
        return pendingRequest;
      }
      return this.pendingRequest.get();
    }

  }

  private static class RestRequestSupplier implements Supplier<Optional<RestRequest>> {

    private final WeakReference<RestRequest> restRequest;

    RestRequestSupplier(final RestRequest restRequest) {
      this.restRequest = new WeakReference<>(restRequest);
    }

    @Override
    public Optional<RestRequest> get() {
      return Optional.ofNullable(this.restRequest.get());
    }

  }

}
