package dev.chux.gcp.crun.internal.app;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import io.lettuce.core.api.async.RedisAsyncCommands;

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

  private static final String KIND_SYNC = "sync";
  private static final String KIND_ASYNC = "async";
  private static final String KIND_SYNC_RANDOM = "sync_random";
  private static final String KIND_ASYNC_RANDOM = "async_random";
  private static final String KIND_PIPELINE = "pipeline";
  private static final String KIND_PIPELINE_ALL = "pipeline_all";

  static {
    REDIS_CLIENT.setOptions(REDIS_CLIENT_OPTIONS);
  }

  private static final int SMALL_KEYS = Integer.parseInt(System.getenv("SMALL_KEYS"));
  private static final String SMALL_KEY_PREFIX = System.getenv("SMALL_KEY_PREFIX");

  private static final int MEDIUM_KEYS = Integer.parseInt(System.getenv("MEDIUM_KEYS"));
  private static final String MEDIUM_KEY_PREFIX = System.getenv("MEDIUM_KEY_PREFIX");

  private static final int LARGE_KEYS = Integer.parseInt(System.getenv("LARGE_KEYS"));
  private static final String LARGE_KEY_PREFIX = System.getenv("LARGE_KEY_PREFIX");

  private static final String[] KEY_PREFIXES = 
      new String[] { SMALL_KEY_PREFIX, MEDIUM_KEY_PREFIX, LARGE_KEY_PREFIX };
  private static final Integer[] KEY_ROUNDS = new Integer[] { SMALL_KEYS, MEDIUM_KEYS, LARGE_KEYS };

  private StatefulRedisConnection<String, String> redisConnection;

  @PostConstruct
  private void onPostConstruct() {
    this.redisConnection = REDIS_CLIENT.connect();
    System.out.println("new REDIS_CONNECTION: " + this.redisConnection);
  }

  @GetMapping("/")
  public ResponseEntity<String> 
  root(final HttpServletRequest request,
      @RequestParam(value="kind", required=true) String kind) {

    switch(kind) {

      case KIND_ASYNC: {
        testAsync();
        break;
      }

      case KIND_ASYNC_RANDOM: {
        testAsyncRandom();
        break;
      }

      case KIND_SYNC_RANDOM: {
        testSyncRandom();
        break;
      }

      case KIND_PIPELINE: {
        testPipelining();
        break;
      }

      case KIND_PIPELINE_ALL: {
        testPipeliningAll();
        break;
      }

      case KIND_SYNC:
      default: {
        testSync();
        break;
      }

    }
    
    return ResponseEntity.ok().body(kind);
  }

  private void testSync() {
    RedisController.sequentialTest();
  }

  private void testSyncRandom() {
    final long latency = RedisController.nonSequentialTest();
    System.out.println("total latency (ns): " + latency);
  }

  private void testAsync() {
    final Thread redisWorker = (new Thread() {
      public void run(){
        RedisController.sequentialTest();
      }
    });
    redisWorker.start();
  }

  private void testAsyncRandom() {
    final Thread redisWorker = (new Thread() {
      public void run(){
        final long latency = RedisController.nonSequentialTest();
        System.out.println("total latency (ns): " + latency);
      }
    });
    redisWorker.start();
  }

  private void testPipelining() {
    RedisController.sequentialPipeliningTest();
  }

  private void testPipeliningAll() {
    RedisController.nonSequentialPipeliningTest(this.redisConnection);
  }

  private static void sequentialTest() {
    final long smallKeysLatency = (SMALL_KEYS == 0)? 0l 
      : RedisController.sequentialTest(SMALL_KEY_PREFIX, SMALL_KEYS)/SMALL_KEYS;
    final long mediumKeysLatency = (MEDIUM_KEYS == 0)? 0l 
      : RedisController.sequentialTest(MEDIUM_KEY_PREFIX, MEDIUM_KEYS)/MEDIUM_KEYS;
    final long largeKeysLatency = (LARGE_KEYS == 0)? 0l
      : RedisController.sequentialTest(LARGE_KEY_PREFIX, LARGE_KEYS)/LARGE_KEYS;

    System.out.println("GET " + Integer.toString(SMALL_KEYS, 10) + " SMALL_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + smallKeysLatency);
    System.out.println("GET " + Integer.toString(MEDIUM_KEYS, 10) + " MEDIUM_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + mediumKeysLatency);

    System.out.println("GET " + Integer.toString(LARGE_KEYS, 10) + " LARGE_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + largeKeysLatency);
  }

  /**
   * returns: latency of all the operations in nanoseconds
   */
  private static long nonSequentialTest() {

    final AtomicInteger[] buckets = new AtomicInteger[]{ new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0) };

    final String[] keyPrefixes = new String[] { SMALL_KEY_PREFIX, MEDIUM_KEY_PREFIX, LARGE_KEY_PREFIX };
    final Integer[] keyRounds = new Integer[] { SMALL_KEYS, MEDIUM_KEYS, LARGE_KEYS };
   
    final long[] keyLatencies = new long[] { 0l, 0l, 0l };

    final int sizeOfKeyPrefixes = KEY_PREFIXES.length;
    final int rounds = Arrays.asList(KEY_ROUNDS).stream().mapToInt(Integer::intValue).sum();

    final StatefulRedisConnection<String, String> connection = REDIS_CLIENT.connect();
    final RedisStringCommands<String, String> sync = connection.sync();
  
    long latency = 0l;
    for(int i = 1 ; i <= rounds ; i++) {
      final String key = getNextKey(buckets, keyPrefixes, keyRounds, rounds);
      final long _latency = getKeyFromRedis(key, sync);
      System.out.println("KEY: " + key + " | latency (ns): " + _latency);
      latency += _latency;
      for( int j = 0 ; j < sizeOfKeyPrefixes ; j++ ) {
        if( key.startsWith(KEY_PREFIXES[j]) ) {
          keyLatencies[j] += _latency;
          break;
        }
      }
    }

    System.out.println("average latency (ns): " + (latency/rounds));
    for(int i = 0 ; i < sizeOfKeyPrefixes ; i++) {
      final String keyPrefix = KEY_PREFIXES[i];
      final String keyType = keyPrefix.substring(0, keyPrefix.length()-1).toUpperCase();
      final int _rounds = KEY_ROUNDS[i];
      final long keysAvgLatency = keyLatencies[i] / ((_rounds == 0)? 1 : _rounds);
      System.out.println("GET " + Integer.toString(_rounds, 10) + " " + keyType + "s average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + keysAvgLatency);
    }

    connection.close();

    return latency;
  }

  private static void sequentialPipeliningTest() {
    final long smallKeysLatency = (SMALL_KEYS == 0)? 0l 
      : RedisController.sequentialPipeliningTest(SMALL_KEY_PREFIX, SMALL_KEYS)/SMALL_KEYS;
    final long mediumKeysLatency = (MEDIUM_KEYS == 0)? 0l 
      : RedisController.sequentialPipeliningTest(MEDIUM_KEY_PREFIX, MEDIUM_KEYS)/MEDIUM_KEYS;
    final long largeKeysLatency = (LARGE_KEYS == 0)? 0l
      : RedisController.sequentialPipeliningTest(LARGE_KEY_PREFIX, LARGE_KEYS)/LARGE_KEYS;

    System.out.println("GET " + Integer.toString(SMALL_KEYS, 10) + " SMALL_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + smallKeysLatency);
    System.out.println("GET " + Integer.toString(MEDIUM_KEYS, 10) + " MEDIUM_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + mediumKeysLatency);
    System.out.println("GET " + Integer.toString(LARGE_KEYS, 10) + " LARGE_KEYs average latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + largeKeysLatency);
  }

  private static void nonSequentialPipeliningTest(final StatefulRedisConnection<String, String> redisConnection) {
    final int rounds = Arrays.asList(KEY_ROUNDS)
      .stream().mapToInt(Integer::intValue).sum();
    final long keysLatency = (rounds == 0)? 0l 
      : RedisController.nonSequentialPipeliningTest(redisConnection, rounds)/rounds;

    System.out.println("GET " + Integer.toString(rounds, 10) + " KEYs latency (ns) @[" + REDIS_REGION + "/" + REDIS_HOST + "]: " + keysLatency);
  }

  /**
   * returns: latency of the operation in nanoseconds
   */
  private static long getKeyFromRedis(final String key,
      final RedisStringCommands<String, String> redis) {
    final long startNanos = System.nanoTime();
    final String value = redis.get(key);
    return System.nanoTime() - startNanos;
  }

  /**
   * returns: latency of all the operations in nanoseconds
   */
  private static long sequentialTest(final String keyPrefix, final int rounds) {

    final StatefulRedisConnection<String, String> connection = REDIS_CLIENT.connect();
    final RedisStringCommands<String, String> sync = connection.sync();

    long latency = 0l;
    for(int i = 1 ; i <= rounds ; i++) {
      final String key = keyPrefix + Integer.toString(i, 10); 
      final long _latency = getKeyFromRedis(key, sync);
      latency += _latency;
    }

    connection.close();

    return latency;
  } 

  /**
   * returns: next key to be fetched, kind will be "random", 
   *          indexes will be sequential per kind.
   */
  private static String getNextKey(final AtomicInteger[] buckets,
      final String[] keyPrefixes, final Integer[] keyRounds, final int maxRounds) {

    final int indexOfLastBucket = buckets.length-1;

    int i = 0;
    int bucket = ThreadLocalRandom.current().nextInt(0, 3);
    AtomicInteger rounds = buckets[bucket];
    while( rounds.incrementAndGet() > keyRounds[bucket] && i++ < maxRounds ) {
      final int _bucket = (bucket == 0)? indexOfLastBucket : bucket-1;
      buckets[bucket] = buckets[_bucket];
      keyPrefixes[bucket] = keyPrefixes[_bucket];
      keyRounds[bucket] = keyRounds[_bucket];
      bucket = _bucket;
      rounds = buckets[_bucket];
    }

    return keyPrefixes[bucket] + rounds.get();
  }

  private static long sequentialPipeliningTest(final String keyPrefix, final int rounds) {

    final StatefulRedisConnection<String, String> connection = REDIS_CLIENT.connect();
    final RedisAsyncCommands<String, String> commands = connection.async();

    commands.setAutoFlushCommands(false);
    connection.setAutoFlushCommands(false);

    final List<RedisFuture<?>> futures = Lists.newArrayListWithCapacity(rounds);

    for(int i = 1 ; i <= rounds ; i++) {
      final String key = keyPrefix + Integer.toString(i, 10); 
      futures.add(commands.get(key));
    }

    final long startNanos = System.nanoTime();
    commands.flushCommands();
    final boolean result = LettuceFutures.awaitAll(60l, 
        TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]));
    final long latency = System.nanoTime() - startNanos;

    connection.close();

    return latency;
  }

  private static long nonSequentialPipeliningTest(final StatefulRedisConnection<String, String> connection, final int rounds) {

    final StatefulRedisConnection<String, String> _connection = 
      ((connection != null) && connection.isOpen())? connection : REDIS_CLIENT.connect();
    final RedisAsyncCommands<String, String> commands = _connection.async();

    System.out.println("using REDIS_CONNECTION: " + _connection);

    commands.setAutoFlushCommands(false);
    _connection.setAutoFlushCommands(false);

    final List<RedisFuture<?>> futures = Lists.newArrayListWithCapacity(rounds);
    final int buckets = KEY_PREFIXES.length;

    int keys = 0;
    int bucket;
    String keyPrefix;
    for(int i = 0 ; i < buckets ; i++) {
      bucket = KEY_ROUNDS[i];
      keyPrefix = KEY_PREFIXES[i];
      for(int j = 1 ; j <= bucket ; j++ ) {
        final String key = keyPrefix + Integer.toString(j, 10); 
        System.out.println("KEY: " + key);
        futures.add(commands.get(key));
        keys += 1;
      }
    }

    final long startNanos = System.nanoTime();
    commands.flushCommands();
    final boolean result = LettuceFutures.awaitAll(60l, 
        TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]));
    final long latency = System.nanoTime() - startNanos;

    if( !_connection.equals(connection) ) {
      _connection.close();
    }

    final Set<Integer> uniqueValues = Sets.newHashSetWithExpectedSize(buckets);
    int values = 0;
    for(final RedisFuture future : futures) {
      try {
        final Object value = future.get();
        if( !uniqueValues.add(value.hashCode()) ) {
          values += 1;
        } else {
          System.out.println("VALUE: " + value.toString().substring(0, 10));
        }
      } catch(Exception e) {
        System.out.println(System.out);
      }
    }
    System.out.println("VALUES: " + uniqueValues);
    System.out.println("KEYS: " + Integer.toString(keys, 10) + " | VALUES: " + Integer.toString(uniqueValues.size(), 10) + " (unique) + " + Integer.toString(values, 10) + "(repeated)");

    return latency;
  }

}
