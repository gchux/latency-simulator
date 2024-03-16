package dev.chux.gcp.crun.internal.app;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;

@RestController
public class RedisController {

  private static final String TRUSTSTORE_PATH = System.getenv("TRUSTSTORE_PATH");
  private static final String TRUSTSTORE_PASS = System.getenv("TRUSTSTORE_PASS");
  private static final SslOptions SSL_OPTIONS = 
    SslOptions.builder().jdkSslProvider()
    .truststore(new File(TRUSTSTORE_PATH), TRUSTSTORE_PASS).build();

  private static final ClientOptions REDIS_CLIENT_OPTIONS = 
    ClientOptions.builder().sslOptions(SSL_OPTIONS).build();
 
  private static final String REDIS_REGION = System.getenv("REDIS_REGION");;
  private static final String REDIS_HOST = System.getenv("REDIS_HOST");;
  private static final String REDIS_PORT = System.getenv("REDIS_PORT");;
  private static final String REDIS_AUTH = System.getenv("REDIS_AUTH");;
  private static final RedisClient REDIS_CLIENT = RedisClient
    .create(RedisURI.Builder.redis(REDIS_HOST, Integer.parseInt(REDIS_PORT, 10))
        .withPassword(REDIS_AUTH).withSsl(true).build());

  private static final String KIND_ASYNC = "async";

  static {
    REDIS_CLIENT.setOptions(REDIS_CLIENT_OPTIONS);
  }

  private static final int SMALL_KEYS = Integer.parseInt(System.getenv("SMALL_KEYS"));
  private static final String SMALL_KEY_PREFIX = System.getenv("SMALL_KEY_PREFIX");

  private static final int MEDIUM_KEYS = Integer.parseInt(System.getenv("MEDIUM_KEYS"));
  private static final String MEDIUM_KEY_PREFIX = System.getenv("MEDIUM_KEY_PREFIX");

  private static final int LARGE_KEYS = Integer.parseInt(System.getenv("LARGE_KEYS"));
  private static final String LARGE_KEY_PREFIX = System.getenv("LARGE_KEY_PREFIX");

  @GetMapping("/")
  public ResponseEntity<String> 
  root(final HttpServletRequest request,
      @RequestParam(value="kind", required=true) String kind) {

    if( kind.equalsIgnoreCase(KIND_ASYNC) ) {
      testAsync();
    } else {
      testSync();
    }
    
    return ResponseEntity.ok().body(kind);
  }

  private void testSync() {
    RedisController.test();
  }

  private void testAsync() {
    final Thread redisWorker = (new Thread() {
      public void run(){
        RedisController.test();
      }
    });
    redisWorker.start();
  }

  private static void test() {
    final long smallKeysLatency = RedisController.testRedis(SMALL_KEY_PREFIX, SMALL_KEYS)/SMALL_KEYS;
    final long mediumKeysLatency = RedisController.testRedis(MEDIUM_KEY_PREFIX, MEDIUM_KEYS)/MEDIUM_KEYS;
    final long largeKeysLatency = RedisController.testRedis(LARGE_KEY_PREFIX, LARGE_KEYS)/LARGE_KEYS;
    System.out.println("GET " + Integer.toString(SMALL_KEYS, 10) + " SMALL_KEYS average latency @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + smallKeysLatency);
    System.out.println("GET " + Integer.toString(MEDIUM_KEYS, 10) + " MEDIUM_KEYS average latency @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + mediumKeysLatency);
    System.out.println("GET " + Integer.toString(LARGE_KEYS, 10) + " LARGE_KEYS average latency @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + largeKeysLatency);
  }

  private static long testRedis(final String keyPrefix, final int rounds) {

    final StatefulRedisConnection<String, String> connection = REDIS_CLIENT.connect();
    final RedisStringCommands<String, String> sync = connection.sync();

    long latency = 0l;
    
    for(int i = 1 ; i <= rounds ; i++) {
      final String key = keyPrefix + Integer.toString(i, 10); 
      final long startNanos = System.nanoTime();
      final String value = sync.get(key);
      final long _latency = System.nanoTime() - startNanos;
      latency += _latency;
      // System.out.println("KEY[" + key + "]=" + value.length() + " | latency=" + Long.toString(_latency, 10));
    }
    
    return latency;
  } 

}
