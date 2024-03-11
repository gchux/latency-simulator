package dev.chux.gcp.crun.internal.app;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import static dev.chux.gcp.crun.Application.getLatency;

@RestController
public class SimpleHttpRequestRestController {

  private static final Boolean FAKE_500S = Boolean.FALSE;

  // latency in seconds
  private static final int MIN_LATENCY = 5;
  private static final int MAX_LATENCY = 25;

  private static final int LATENCY_SPIKE_FACTOR = 9;

  private static final AtomicLong counter = new AtomicLong(0L);

  @Value("${app.response.minLatency}")
  private int minResponseLatecy;

  @Value("${app.response.maxLatency}")
  private int maxResponseLatecy;

  @Value("${app.response.latencySpikeFactor}")
  private int latencySpikeFactor;

  @GetMapping("/simpleHttpRequest/host:{host}/port:{port}")
  public ResponseEntity<String> test(final HttpServletRequest request,
      @PathVariable String host, @PathVariable Integer port, 
      @RequestParam(required = true) String path) {
    System.out.println("RestController-enter: " + request);

    final int latency = getResponseLatency();

    System.out.println(request + " latency = " + Integer.toString(latency, 10));

    try {
      Thread.sleep(latency); // simulate latency
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }

    simpleHttpRequest(host, port.intValue(), path);

    System.out.println("RestController-exit: " + request);
    return ResponseEntity.ok().body("");
  }

  private static boolean shouldSpikeLatency() {
    final boolean thirdMillis = System.currentTimeMillis()%3L == 0;
    final boolean fifthRequest = counter.incrementAndGet()%5L == 0;
    return fifthRequest && thirdMillis;
  }

  private int getResponseLatency() {
    // calculate latency to be introduced
    final int baseLatency = getLatency(minResponseLatecy, maxResponseLatecy)*1000;
    // introduce even more latency "randomly"
    return (shouldSpikeLatency()? latencySpikeFactor*baseLatency : baseLatency)/9;
  }

  private static void simpleHttpRequest(final String host, final int port, final String path) {
    final String _url = "http://" + host + ":" + Integer.toString(port, 10) + path;
    System.out.println("GET " + _url);
    try {
      final URL url = new URL(_url);
      final URLConnection connection = url.openConnection();

      final BufferedReader in = new BufferedReader(
          new InputStreamReader(connection.getInputStream()));
      String inputLine;
      while ((inputLine = in.readLine()) != null) 
        System.out.println(inputLine);
      in.close();
    } catch(Exception e) {
      e.printStackTrace(System.err); 
    }
  }

}
