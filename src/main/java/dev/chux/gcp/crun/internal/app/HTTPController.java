package dev.chux.gcp.crun.internal.app;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
//import io.netty.channel.socket.nio.NioChannelOption;
//import jdk.net.ExtendedSocketOptions;

import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@RestController
public class HTTPController {

  final ConnectionProvider CONNECTION_PROVIDER = ConnectionProvider.builder("test")
    .maxConnections(1).maxLifeTime(Duration.ofMinutes(10)).build();

  // see: https://github.com/reactor/reactor-netty/blob/v1.1.20/reactor-netty-examples/src/main/java/reactor/netty/examples/documentation/http/client/channeloptions/Application.java#L28-L42
  final HttpClient HTTP_CLIENT = HttpClient.create(CONNECTION_PROVIDER)
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
  final ReactorNettyClientRequestFactory CLIENT_FACTORY = new ReactorNettyClientRequestFactory(HTTP_CLIENT);
  final ReactorClientHttpConnector CLIENT_CONNECTOR = new ReactorClientHttpConnector(HTTP_CLIENT);

  final WebClient WEB_CLIENT = WebClient.builder().clientConnector(CLIENT_CONNECTOR).build();
  final RestTemplate REST_TEMPLATE = new RestTemplate(CLIENT_FACTORY);

  final String TEST_URI = "https://httpbin.org/post";

  final AtomicLong REQUESTS_COUNTER = new AtomicLong(0);

  @PostConstruct
  private void onPostConstruct() {}

  @GetMapping("/")
  public ResponseEntity<String> 
  root(final HttpServletRequest request) {
    long serial = REQUESTS_COUNTER.incrementAndGet();
    System.out.println("REQ[serial=" + Long.toString(serial, 10) + "]");
    return (serial%2==0)? restTemplate(request) : webFlux(request);
  }

  @GetMapping("/restTemplate")
  public ResponseEntity<String> 
  restTemplate(final HttpServletRequest request) {
    System.out.println("REQ[provider=RestTemplate]");
    final String data = REST_TEMPLATE
      .postForObject(TEST_URI, null, String.class);
    return ResponseEntity.ok().body(data);
  }

  @GetMapping("/webFlux")
  public ResponseEntity<String> 
  webFlux(final HttpServletRequest request) {
    System.out.println("REQ[provider=WebClient]");
    // blocking makes no sense in reactive programming
    // so this is just a sample to show TCP keepalives
    final String data = WEB_CLIENT
      .post().uri(TEST_URI).retrieve()
      .bodyToMono(String.class).block();
    return ResponseEntity.ok().body(data);
  }

}
