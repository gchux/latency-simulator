package dev.chux.gcp.crun.internal.app;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import jakarta.servlet.http.HttpServletRequest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
//import io.netty.channel.socket.nio.NioChannelOption;
//import jdk.net.ExtendedSocketOptions;

import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import static dev.chux.gcp.crun.Utils.getLatency;

@RestController
public class HTTPController {

  private static final String X_REQUEST_URL = "X-Request-URL";
  private static final String X_CLOUD_TRACE_CONTEXT = "X-Cloud-Trace-Context";

  private static final String INSTRUMENTATION_SCOPE_NAME = HTTPController.class.getName();
  private static final AtomicLong COUNTER = new AtomicLong(0);
  
  private final Logger logger = LoggerFactory.getLogger(HTTPController.class);

  @Value("${app.response.minLatency}")
  int minResponseLatecy;

  @Value("${app.response.maxLatency}")
  int maxResponseLatecy;

  @Value("${app.response.latencySpikeFactor}")
  int latencySpikeFactor;

  private final OpenTelemetrySdk openTelemetrySdk;

  @Autowired
  public HTTPController(OpenTelemetrySdk openTelemetrySdk) {
    this.openTelemetrySdk = openTelemetrySdk;
  }

  final ConnectionProvider CONNECTION_PROVIDER = ConnectionProvider.builder("test")
    .maxConnections(1).maxLifeTime(Duration.ofMinutes(10)).build();

  // see: https://github.com/reactor/reactor-netty/blob/v1.1.20/reactor-netty-examples/src/main/java/reactor/netty/examples/documentation/http/client/channeloptions/Application.java#L28-L42
  private final HttpClient HTTP_CLIENT = HttpClient.create(CONNECTION_PROVIDER)
    .protocol(HttpProtocol.HTTP11).responseTimeout(Duration.ofMillis(5000))
	  .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) //<1>
	  .option(ChannelOption.SO_KEEPALIVE, true)            //<2>
	  // The options below are available only when NIO transport (Java 11) is used
	  // on Mac or Linux (Java does not currently support these extended options on Windows)
	  // https://bugs.openjdk.java.net/browse/JDK-8194298
	  //.option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE), 300)
	  //.option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL), 60)
	  //.option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT), 8);
	  // The options below are available only when Epoll transport is used
	  .option(EpollChannelOption.TCP_KEEPIDLE, 10)         //<3>
	  .option(EpollChannelOption.TCP_KEEPINTVL, 5)         //<4>
	  .option(EpollChannelOption.TCP_KEEPCNT, 8);          //<5>

  // both factories will use the same underlying instance of `reactor.netty.http.client.HttpClient`
  // see: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ReactorNettyClientRequestFactory.html
  // see: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/reactive/ReactorClientHttpConnector.html
  private final ReactorNettyClientRequestFactory CLIENT_FACTORY = new ReactorNettyClientRequestFactory(HTTP_CLIENT);
  private final ReactorClientHttpConnector CLIENT_CONNECTOR = new ReactorClientHttpConnector(HTTP_CLIENT);

  private final WebClient WEB_CLIENT = WebClient.builder().clientConnector(CLIENT_CONNECTOR).build();
  private final RestTemplate REST_TEMPLATE = new RestTemplate(CLIENT_FACTORY);

  private final String TEST_URI = "https://httpbin.org/post";

  private final AtomicLong REQUESTS_COUNTER = new AtomicLong(0);

  private int getResponseLatency() {
    // calculate latency to be introduced
    final int baseLatency = getLatency(minResponseLatecy, maxResponseLatecy)*1000;
    // introduce even more latency "randomly"
    return (HTTPController.shouldSpikeLatency()? latencySpikeFactor*baseLatency : baseLatency)/9;
  }

  private static boolean shouldSpikeLatency() {
    final boolean thirdMillis = System.currentTimeMillis()%3L == 0;
    final boolean fifthRequest = COUNTER.incrementAndGet()%5L == 0;
    return fifthRequest && thirdMillis;
  }

  @PostConstruct
  void onPostConstruct() {}

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
      final ResponseEntity<String> responseEntity = ResponseEntity.ok()
        .header(X_REQUEST_URL, request.getRequestURL().toString()).body("OK");
      span.addEvent("done");
      return responseEntity;
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
      final String event = "processing-request[" + serial + "]/" + (isEven? "RestTemplate" : "WebClient");
      logger.info(event);
      span.addEvent("before/" + event);
      final ResponseEntity<String> responseEntity = isEven?
        restTemplate(request, traceCtx) : webClient(request, traceCtx);
      span.addEvent("after/" + event);
      scope.close();
      return responseEntity;
    } finally {
      span.end();
    }
  }

  @GetMapping("/restTemplate")
  public ResponseEntity<String> 
  restTemplate(final HttpServletRequest request,
               @RequestHeader("X-Cloud-Trace-Context") Optional<String> traceCtx) {
    final Span span = getSpanWithDesciption("RestTemplate");
    try( Scope scope = span.makeCurrent() ) {
      logger.info("before/request/RestTemplate");
      span.addEvent("before/request");
      final String data = REST_TEMPLATE
        .postForObject(TEST_URI, null, String.class);
      span.addEvent("after/request");
      logger.info("after/request/RestTemplate");
      scope.close();
      return ResponseEntity.ok()
        .header(X_REQUEST_URL, request.getRequestURL().toString())
        .header(X_CLOUD_TRACE_CONTEXT, traceCtx.orElse(""))
        .body(data);
    } finally {
      span.end();
    }
  }

  @GetMapping("/webClient")
  public ResponseEntity<String> 
  webClient(final HttpServletRequest request,
            @RequestHeader("X-Cloud-Trace-Context") Optional<String> traceCtx) {
    final Span span = getSpanWithDesciption("WebClient");
    try( Scope scope = span.makeCurrent() ) {
      logger.info("before/request/WebClient");
      span.addEvent("before/request");
      // blocking makes no sense in reactive programming
      // so this is just a sample to show TCP keepalives
      final String data = WEB_CLIENT
        .post().uri(TEST_URI).retrieve()
        .bodyToMono(String.class).block();
      span.addEvent("after/request");
      logger.info("after/request/WebClient");
      scope.close();
      return ResponseEntity.ok()
        .header(X_REQUEST_URL, request.getRequestURL().toString())
        .header(X_CLOUD_TRACE_CONTEXT, traceCtx.orElse(""))
        .body(data);
    } finally {
      span.end();
    }
  }

}
