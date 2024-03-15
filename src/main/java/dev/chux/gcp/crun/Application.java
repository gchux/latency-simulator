package dev.chux.gcp.crun;

import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.Properties;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.Banner;

import java.util.Map;
import java.util.HashMap;

import dev.chux.gcp.crun.web.WebModule;
import dev.chux.gcp.crun.web.RequestsQueue;
import dev.chux.gcp.crun.internal.RestModule;

@SpringBootConfiguration
public class Application {

  private static final String ENV_LATENCY_PROFILE = "LATENCY_PROFILE";
  private static final String LATENCY_PROFILE = "/profiles/default";

  // latency in seconds
  private static final String MIN_STARTUP_LATENCY = "app.startup.minLatency";
  private static final String MAX_STARTUP_LATENCY = "app.startup.maxLatency";
  private static final String COLDSTART_SPIKE_FACTOR = "app.coldstart.spikeFactor";

  public static final int getLatency(int lower, int upper) {
    final int latency = (int) (Math.random()*(upper-lower))+lower;
    return (latency < 0)? -1*latency : latency;
  }

  public static void main(final String[] args) {

    final Optional<String> latencySettings = Optional.ofNullable(System.getenv(ENV_LATENCY_PROFILE));
    final Properties properties = loadProperties(latencySettings.orElse(LATENCY_PROFILE));

    final int startupLatency = getStartupLatency(properties);

    System.out.println("startup latency = " + Integer.toString(startupLatency, 10));

    final String[] _args = new String[]{};

    final Map<String, Object> settings = new HashMap<>();
    settings.put("server.port", Integer.valueOf(8080));
    final ConfigurableApplicationContext ctx = startApplication(_args, properties, settings);

    System.out.println("SpringBoot Context: " + ctx);
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
    System.out.println(properties);
    return properties;
  }

  private static int getStartupLatency(final Properties properties) {
    final int minStartupLatency = getMinStartupLatency(properties);
    final int maxStartupLatency = getMaxStartupLatency(properties);
    final int coldstartSpikeFactor = getColdstartSpikeFactor(properties);
    final int baseLatency = getLatency(minStartupLatency, maxStartupLatency)*1000;
    final boolean spikeLatency = System.currentTimeMillis()%3 == 0;
    return spikeLatency? coldstartSpikeFactor*baseLatency : baseLatency;
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
