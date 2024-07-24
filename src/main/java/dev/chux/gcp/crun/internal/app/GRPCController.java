package dev.chux.gcp.crun.internal.app;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.api.core.ApiFunction;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.grpc.ChannelPoolSettings;
import com.google.api.gax.grpc.ChannelPrimer;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.aiplatform.v1beta1.EndpointServiceClient;
import com.google.cloud.aiplatform.v1beta1.EndpointServiceClient.ListEndpointsPagedResponse;
import com.google.cloud.aiplatform.v1beta1.ListEndpointsRequest;
import com.google.cloud.aiplatform.v1beta1.ListEndpointsResponse;
import com.google.cloud.aiplatform.v1beta1.EndpointServiceSettings;
import com.google.cloud.aiplatform.v1beta1.stub.EndpointServiceStubSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.chux.gcp.crun.Utils.getLatency;

@RestController
public class GRPCController {

  private static final String X_REQUEST_URL = "X-Request-URL";
  private static final String X_CLOUD_TRACE_CONTEXT = "X-Cloud-Trace-Context";
  private static final AtomicLong REQUESTS_COUNTER = new AtomicLong(0);

  private static final String INSTRUMENTATION_SCOPE_NAME = GRPCController.class.getName();
  private static final AtomicLong COUNTER = new AtomicLong(0);

  private static boolean shouldSpikeLatency() {
    final boolean thirdMillis = System.currentTimeMillis()%3L == 0;
    final boolean fifthRequest = COUNTER.incrementAndGet()%5L == 0;
    return fifthRequest && thirdMillis;
  }
  
  private static final Logger logger = LoggerFactory.getLogger(GRPCController.class);

  private static final String PROJECT_ID = System.getProperty("gcp.project_id");
  private static final String GCP_LOCATION = System.getProperty("gcp.location");
  private static final String GCP_PARENT = "projects/" + PROJECT_ID;
  private static final String AIP_LOCATION = System.getProperty("googleapis.location");
  private static final String AIP_ENDPOINT = AIP_LOCATION + "-aiplatform.googleapis.com:443";
  private static final String AIP_PARENT = GCP_PARENT + "/locations/" + AIP_LOCATION;

  @Value("${app.response.minLatency}")
  int minResponseLatecy;

  @Value("${app.response.maxLatency}")
  int maxResponseLatecy;

  @Value("${app.response.latencySpikeFactor}")
  int latencySpikeFactor;

  private final OpenTelemetrySdk openTelemetrySdk;

  @Autowired
  public GRPCController(OpenTelemetrySdk openTelemetrySdk) {
    this.openTelemetrySdk = openTelemetrySdk;
  }

  private ClientContext clientContext = null;
  private EndpointServiceClient endpointServiceClient = null;

  @PostConstruct
  void onPostConstruct() {
    final ClientContext.Builder clientContextBuilder = ClientContext.newBuilder();
    clientContextBuilder
      .setQuotaProjectId(PROJECT_ID)
      .setHeaders(ImmutableMap.of("x-grpc-proxy-endpoint", AIP_LOCATION));

    // this.clientContext = clientContextBuilder.build();
    
    InstantiatingGrpcChannelProvider.Builder channelProviderBuilder = InstantiatingGrpcChannelProvider.newBuilder();

    channelProviderBuilder
      .setMaxInboundMessageSize(Integer.MAX_VALUE)
      .setChannelPoolSettings(ChannelPoolSettings.staticallySized(1))
      // .setKeepAliveTime(org.threeten.bp.Duration.ofSeconds(10))
      // .setKeepAliveTimeout(org.threeten.bp.Duration.ofSeconds(10))
      .setKeepAliveTime(org.threeten.bp.Duration.ofSeconds(60))
      .setKeepAliveTimeout(org.threeten.bp.Duration.ofSeconds(10))
      .setKeepAliveWithoutCalls(true)
      .setChannelPrimer(new ChannelPrimer() {
        @Override public void primeChannel(final ManagedChannel managedChannel) {
          logger.info("ManagedChannel: {}", managedChannel);
          managedChannel.notifyWhenStateChanged(managedChannel.getState(true), 
          new Runnable() {
            @Override public void run() {
              logger.info("{} is {}", managedChannel.authority(), managedChannel.getState(false));
            }
          });
        }
      })
      .setChannelConfigurator(new ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>() {
        @Override public ManagedChannelBuilder apply(ManagedChannelBuilder builder) {
          logger.info("ManagedChannelBuilder: {} | {}", builder, builder.getClass().getName());
          if( builder instanceof io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder ) {
            logger.info("ManagedChannelBuilder – TCP KeepAlive config: {}", builder);
            ((io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder) builder)
              .withOption(io.grpc.netty.shaded.io.netty.channel.ChannelOption.SO_KEEPALIVE , Boolean.TRUE)
              .withOption(io.grpc.netty.shaded.io.netty.channel.epoll.EpollChannelOption.TCP_KEEPIDLE, 10)
              .withOption(io.grpc.netty.shaded.io.netty.channel.epoll.EpollChannelOption.TCP_KEEPINTVL, 10)
              .withOption(io.grpc.netty.shaded.io.netty.channel.epoll.EpollChannelOption.TCP_KEEPCNT, 10);
          }
          // see: https://grpc.github.io/grpc-java/javadoc/io/grpc/ManagedChannelBuilder
          return builder.usePlaintext().defaultLoadBalancingPolicy("round_robin");
        }
      });

    final EndpointServiceSettings.Builder endpointServiceSettingsBuilder = EndpointServiceSettings.newBuilder();
    
    endpointServiceSettingsBuilder
      .setEndpoint("grpc.local:5001")
      .setQuotaProjectId(PROJECT_ID)
      .setTransportChannelProvider(channelProviderBuilder.build())
      .setHeaderProvider(FixedHeaderProvider.create(
        ImmutableMap.of(
          "x-grpc-proxy-project", PROJECT_ID,
          "x-grpc-proxy-location", AIP_LOCATION,
          "x-grpc-proxy-endpoint", AIP_ENDPOINT
        )
      ));

    endpointServiceSettingsBuilder
      .getStubSettingsBuilder()
      .listEndpointsSettings()
      .setRetrySettings(
        endpointServiceSettingsBuilder
        .getEndpointSettings()
        .getRetrySettings()
        .toBuilder()
        .setTotalTimeout(org.threeten.bp.Duration.ofSeconds(30))
        .build());

    try {
      final EndpointServiceSettings endpointServiceSettings = endpointServiceSettingsBuilder.build();
      this.endpointServiceClient = EndpointServiceClient.create(endpointServiceSettings);
    } catch(Exception e) {
      e.printStackTrace(System.err);
    }
  }

  private int getResponseLatency() {
    // calculate latency to be introduced
    final int baseLatency = getLatency(minResponseLatecy, maxResponseLatecy)*1000;
    // introduce even more latency "randomly"
    return (GRPCController.shouldSpikeLatency()? latencySpikeFactor*baseLatency : baseLatency)/9;
  }

  private Span getSpanWithDesciption(final String description) {
    return this.openTelemetrySdk
      .getTracer(INSTRUMENTATION_SCOPE_NAME)
      .spanBuilder(description).startSpan();
  }

  @GetMapping("/startupProbe")
  public ResponseEntity<String> 
  startupProbe(final HttpServletRequest request,
               @RequestHeader(X_CLOUD_TRACE_CONTEXT) Optional<String> traceCtx) {
    logger.info("startup-probe: {}", request);
    final Span span = getSpanWithDesciption("startup-probe");
    try ( Scope scope = span.makeCurrent() ) {
      span.addEvent("init");
      Preconditions.checkNotNull(this.endpointServiceClient, "EndpointServiceClient is NULL");
      final ResponseEntity<String> responseEntity = ResponseEntity.ok()
        .header(X_REQUEST_URL, request.getRequestURL().toString()).body("OK");
      span.addEvent("done");
      return responseEntity;
    } catch(Exception e) {
      e.printStackTrace(System.err);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    } finally {
      span.end();
    }
  }

  @GetMapping("/")
  public ResponseEntity<String> 
  root(final HttpServletRequest request,
       @RequestHeader("X-Cloud-Trace-Context") Optional<String> traceCtx) {
    final int latency = getResponseLatency();
    logger.info("{} | latency = {}", request, Integer.toString(latency, 10));

    final Span span = getSpanWithDesciption("root");

    try( Scope scope = span.makeCurrent() ) {
      span.addEvent("before-latency");
      Thread.sleep(latency); // simulate latency
      span.addEvent("after-latency");
      scope.close();
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }

    try( Scope scope = span.makeCurrent() ) {
      long serial = REQUESTS_COUNTER.incrementAndGet();
      System.out.println("REQ[serial=" + Long.toString(serial, 10) + "]");
      final boolean isEven = (serial%2==0);
      final String event = "processing-request[" + serial + "]";
      logger.info(event);
      span.addEvent("before/" + event);

      // see: https://github.com/googleapis/sdk-platform-java/blob/main/gax-java/gax-grpc/src/main/java/com/google/api/gax/grpc/GrpcCallContext.java
      ApiCallContext context = GrpcCallContext.createDefault()
      .withRetrySettings(RetrySettings.newBuilder()
        .setInitialRetryDelay(org.threeten.bp.Duration.ofMillis(10L))
        .setInitialRpcTimeout(org.threeten.bp.Duration.ofMillis(100L))
        .setMaxAttempts(10)
        .setMaxRetryDelay(org.threeten.bp.Duration.ofSeconds(10L))
        .setMaxRpcTimeout(org.threeten.bp.Duration.ofSeconds(30L))
        .setRetryDelayMultiplier(1.4)
        .setRpcTimeoutMultiplier(1.5)
        .setTotalTimeout(org.threeten.bp.Duration.ofMinutes(10L))
        .build()
      )
      .withRetryableCodes(Sets.newHashSet(
        StatusCode.Code.UNAVAILABLE,
        StatusCode.Code.DEADLINE_EXCEEDED
      ))
      .withExtraHeaders(ImmutableMap.of(
          "X-Cloud-Trace-Context", ImmutableList.of(traceCtx.orElse(""))
      ));

      final ListEndpointsRequest.Builder aipRequest = ListEndpointsRequest.newBuilder().setParent(AIP_PARENT);
      final UnaryCallable<ListEndpointsRequest, ListEndpointsResponse> callable = 
        this.endpointServiceClient.listEndpointsCallable().withDefaultCallContext(context);
      final ListEndpointsResponse aipResponse = callable.call(aipRequest.build(), context);
      
      final ResponseEntity<String> responseEntity = ResponseEntity.ok().body(Iterables.toString(aipResponse.getEndpointsList()));
      
      span.addEvent("after/" + event);
      scope.close();
      return responseEntity;
    } finally {
      span.end();
    }
  }

}
