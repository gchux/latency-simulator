package dev.chux.gcp.crun;

import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.Banner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import dev.chux.gcp.crun.web.WebModule;
import dev.chux.gcp.crun.web.RequestsQueue;
import dev.chux.gcp.crun.internal.RestModule;

@SpringBootConfiguration
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  private static final String ENV_SERVER_PORT = "PORT";
  private static final String ENV_LATENCY_PROFILE = "LATENCY_PROFILE";

  private static final String DEFAULT_LATENCY_PROFILE = "/profiles/default";
  private static final String DEFAULT_SERVER_PORT = "8080";

  // latency in seconds
  private static final String STARTUP_LATENCY_ENABLED = "app.startup.latency.enabled";
  private static final String MIN_STARTUP_LATENCY = "app.startup.latency.min";
  private static final String MAX_STARTUP_LATENCY = "app.startup.latency.max";
  private static final String COLDSTART_SPIKE_FACTOR = "app.coldstart.spikeFactor";

  public static void main(final String[] args) {

    final Map<String, String> environment = ImmutableMap.copyOf(System.getenv());

    final Properties profileProperties = getProperies(environment);
    logger.info("proile properties: {}", profileProperties);

    final int serverPort = getServerPort(environment);
    logger.info("server port = {}", serverPort);

    if (isStartupLatencyEnabled(profileProperties)) {
      applyStartupLatency(profileProperties);
    } else {
      logger.info("startup latency disabled");
    }

    final String[] _args = new String[]{};

    final Map<String, Object> settings = new HashMap<>();
    settings.put("server.port", serverPort);

    final ConfigurableApplicationContext ctx = startApplication(_args, profileProperties, settings);

    logger.info("SpringBoot Context: {}", ctx);
  }

  private static void applyStartupLatency(final Properties profileProperties) {
    final int startupLatency = getStartupLatency(profileProperties);
    logger.info("startup latency = {}", Integer.toString(startupLatency, 10));

    try {
      Thread.sleep(startupLatency); // simulate cold-start
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }
  }

  private static Integer getServerPort(final Map<String, String> environment) {
    final String serverPortStr = environment.getOrDefault(ENV_SERVER_PORT, DEFAULT_SERVER_PORT);
    return Integer.parseInt(serverPortStr, 10);
  }

  private static Properties getProperies(final Map<String, String> environment) {
    final String latencyProfile = environment.getOrDefault(ENV_LATENCY_PROFILE, DEFAULT_LATENCY_PROFILE);
    return loadProperties(latencyProfile);
  }

  private static ConfigurableApplicationContext startApplication(final String[] args,
      final Properties properties, final Map<String, Object> settings) {

    final SpringApplication application = new SpringApplicationBuilder(WebModule.class)
      .bannerMode(Banner.Mode.OFF).properties(settings).properties(properties).build();

    final ConfigurableApplicationContext parent = application.run(args);
    final ConfigurableApplicationContext child = new SpringApplicationBuilder().bannerMode(Banner.Mode.OFF)
      .sources(Application.class).parent(parent).child(RestModule.class).web(WebApplicationType.NONE).run(args);

    return parent;
  }

  private static Properties loadProperties(final String latencyProfile) {
    final Properties properties = new Properties();
    try (final InputStream input = new FileInputStream(latencyProfile + ".properties")) {
      properties.load(input);
    } catch(Exception e) {
      e.printStackTrace(System.err);
    }
    return properties;
  }

  private static int getStartupLatency(final Properties properties) {
    final int minStartupLatency = getMinStartupLatency(properties);
    final int maxStartupLatency = getMaxStartupLatency(properties);
    final int coldstartSpikeFactor = getColdstartSpikeFactor(properties);
    final int baseLatency = Utils.getLatency(minStartupLatency, maxStartupLatency)*1000;
    final boolean spikeLatency = System.currentTimeMillis()%3 == 0;
    return spikeLatency? coldstartSpikeFactor*baseLatency : baseLatency;
  }

  private static boolean isStartupLatencyEnabled(final Properties properties) {
    return getBoolProperty(properties, STARTUP_LATENCY_ENABLED, false);
  }

  private static int getMinStartupLatency(final Properties properties) {
    return getIntProperty(properties, MIN_STARTUP_LATENCY, 0);
  }

  private static int getMaxStartupLatency(final Properties properties) {
    return getIntProperty(properties, MAX_STARTUP_LATENCY, 1);
  } 

  private static int getColdstartSpikeFactor(final Properties properties) {
    return getIntProperty(properties, COLDSTART_SPIKE_FACTOR, 1);
  } 

  private static boolean getBoolProperty(final Properties properties, final String key, final boolean defaultValue) {
    final Optional<String> value = getProperty(properties, key);
    if( value.isPresent() ) {
      return Boolean.parseBoolean(value.get());
    }
    return defaultValue;
  }

  private static int getIntProperty(final Properties properties, final String key, final int defaultValue) {
    final Optional<String> value = getProperty(properties, key);
    if( value.isPresent() ) {
      return Integer.parseInt(value.get(), 10);
    }
    return defaultValue;
  }

  private static Optional<String> getProperty(final Properties properties, final String key) {
    return Optional.ofNullable(properties.getProperty(key));
  }

}
