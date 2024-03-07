package dev.chux.gcp.crun.internal.app;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.http.ResponseEntity;

import static dev.chux.gcp.crun.Application.getLatency;

@RestController
public class TestRestController {

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

  @GetMapping("/")
  public ResponseEntity<String> test(final HttpServletRequest request) {
    System.out.println("RestController-enter: " + request);

    final int latency = getResponseLatency();

    System.out.println(request + " latency = " + Integer.toString(latency, 10));

    try {
      Thread.sleep(latency); // simulate latency
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }

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

}
