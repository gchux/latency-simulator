package dev.chux.gcp.crun.internal;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.chux.gcp.crun.web.RequestsQueue;
import dev.chux.gcp.crun.web.RestHandler;
import dev.chux.gcp.crun.internal.app.AppConfig;

import static dev.chux.gcp.crun.Utils.getLatency;

@Configuration
//@EnableAutoConfiguration
@ComponentScan(
  basePackages = {"dev.chux.gcp.crun.internal"}, 
  excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RestServlet.class),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "dev\\.chux\\.gcp\\.crun\\.internal\\.app\\..*")
  }
)
public class RestModule {
  private static final Logger logger = LoggerFactory.getLogger(RestModule.class);

  @Value("${app.initialization.latency.enabled}")
  private boolean isInitializationLatencyEnabled;

  @Value("${app.initialization.latency.min}")
  private int minInitializationLatecy;

  @Value("${app.initialization.latency.max}")
  private int maxInitializationLatecy;

  @Value("${app.initialization.latency.spikeFactor}")
  private int latencySpikeFactor;

  public static final int getLatency(int lower, int upper) {
    final int latency = (int) (Math.random()*(upper-lower))+lower;
    return (latency < 0)? -1*latency : latency;
  }

  private int getInitizalizationLatency() {
    final int baseLatency = getLatency(minInitializationLatecy, maxInitializationLatecy)*1000;
    final boolean spikeLatency = System.currentTimeMillis()%3 == 0;
    return spikeLatency? latencySpikeFactor*baseLatency : baseLatency;
  }

  private void applyInitializationLatency() {
    if (!isInitializationLatencyEnabled) { 
      logger.info("initialization latency disabled");
      return;
    }

    final int latency = getInitizalizationLatency();
    logger.info("initialization latency = {}" + Integer.toString(latency, 10));
    try {
      Thread.sleep(latency);
    } catch(Exception ex) {
      ex.printStackTrace(System.err);
    }
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public RestHandler provideRestServlet(@Qualifier("app-ServletConfig") ServletConfig servletConfig, 
      @Qualifier("app-WebAppContext") WebApplicationContext webAppContext, RequestsQueue requestsQueue) {
    final RestServlet restServlet = new RestServlet(webAppContext);

    try {
      restServlet.initialize(servletConfig, webAppContext);
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
    }

    this.applyInitializationLatency();

    requestsQueue.registerRestHandler(restServlet);
    return restServlet;
  }

  @Bean("app-WebAppContext")
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public WebApplicationContext provideWebAppContext(ApplicationContext applicationContext, ServletContext servletContext) {
    final AnnotationConfigWebApplicationContext webAppContext = new AnnotationConfigWebApplicationContext();
    webAppContext.setParent(applicationContext);
    webAppContext.setServletContext(servletContext);
    webAppContext.register(AppConfig.class);
    return webAppContext;
  }

  @Bean("app-ServletConfig")
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public ServletConfig provideServletConfig(ServletContext servletContext) {
    return new RestServletConfig(servletContext);
  }
  
}
