package dev.chux.gcp.crun.internal.app;

import java.time.Duration;
import java.util.List;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.chux.gcp.crun.Utils.getLatency;

@RestController
public class TestController {
  private static final AtomicLong COUNTER = new AtomicLong(0);

  private static final Logger logger = LoggerFactory.getLogger(TestController.class);

  private static boolean shouldSpikeLatency(final long count) {
    final boolean thirdMillis = System.currentTimeMillis()%3L == 0;
    final boolean fifthRequest = count%5L == 0;
    return fifthRequest && thirdMillis;
  }

  @Value("${app.response.code}")
  int responseCode;
  
  @Value("${app.response.minLatency}")
  int minResponseLatecy;

  @Value("${app.response.maxLatency}")
  int maxResponseLatecy;

  @Value("${app.response.latencySpikeFactor}")
  int latencySpikeFactor;

  @PostConstruct
  void onPostConstruct() { }

  private int getResponseLatency(final HttpServletRequest request, final long count) {
    // calculate latency to be introduced
    final int baseLatency = getLatency(minResponseLatecy, maxResponseLatecy)*1000;
    // introduce even more latency "randomly"
    return (TestController.shouldSpikeLatency(count)? latencySpikeFactor*baseLatency : baseLatency)/9;
  }

  private long applyLatency(final HttpServletRequest request) {
    final long count = COUNTER.incrementAndGet();
    final int latency = this.getResponseLatency(request, count);

    logger.info("{}#{} | latency = {}", request, Long.toString(count, 10), Integer.toString(latency, 10));

    try {
      // simulate latency
      Thread.sleep(latency);
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }

    return count;
  }

  @GetMapping("/livenessProbe")
  public ResponseEntity<String> 
  livenessProbe(final HttpServletRequest request) {
    return ResponseEntity.ok().body("OK");
  }

  @GetMapping("/startupProbe")
  public ResponseEntity<String> 
  startupProbe(final HttpServletRequest request) {
    return ResponseEntity.ok().body("OK");
  }

  @GetMapping("/")
  public ResponseEntity<String> 
  root(final HttpServletRequest request) {
    final long requestID = this.applyLatency(request);
    return ResponseEntity.status(responseCode).body(Long.toString(requestID, 10));
  }

}
