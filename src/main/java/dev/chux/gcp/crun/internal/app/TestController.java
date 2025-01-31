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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

  @Value("${app.response.latency.enabled._root_}")
  boolean latencyEnabled;

  @Value("${app.response.code._root_}")
  int responseCode;
  
  @Value("${app.response.latency.min._root_}")
  int minResponseLatecy;

  @Value("${app.response.latency.max._root_}")
  int maxResponseLatecy;

  @Value("${app.response.latency.spikeFactor._root_}")
  int latencySpikeFactor;

  @PostConstruct
  void onPostConstruct() { }

  private int getResponseLatency(final HttpServletRequest request, final long count) {
    if (!this.latencyEnabled) {
      return 0;
    }

    // calculate latency to be introduced
    final int baseLatency = getLatency(this.minResponseLatecy, this.maxResponseLatecy)*1000;
    // introduce even more latency "randomly"
    return (TestController.shouldSpikeLatency(count)? this.latencySpikeFactor*baseLatency : baseLatency)/9;
  }

  private long applyLatency(final HttpServletRequest request) {
    final long count = TestController.COUNTER.incrementAndGet();
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

  @GetMapping("/startupProbe")
  public ResponseEntity<String> 
  startupProbe(final HttpServletRequest request) {
    return ResponseEntity.ok().body("OK");
  }

  @GetMapping("/livenessProbe")
  public ResponseEntity<String> 
  livenessProbe(final HttpServletRequest request) {
    return ResponseEntity.ok().body("OK");
  }

  @RequestMapping(
    value = "/",
    method = RequestMethod.GET
  )
  public ResponseEntity<String> 
  root(final HttpServletRequest request) {
    final long requestID = this.applyLatency(request);
    return ResponseEntity.status(this.responseCode).body(Long.toString(requestID, 10));
  }

}
