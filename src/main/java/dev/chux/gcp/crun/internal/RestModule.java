package dev.chux.gcp.crun.internal;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import dev.chux.gcp.crun.web.RequestsQueue;
import dev.chux.gcp.crun.web.RestHandler;
import dev.chux.gcp.crun.internal.app.AppConfig;

import static dev.chux.gcp.crun.Application.getLatency;

@Configuration
//@EnableAutoConfiguration
public class RestModule {

  // latency in seconds
  private static final int MIN_STARTUP_LATENCY = 40;
  private static final int MAX_STARTUP_LATENCY = 65;
  private static final int LATENCY_SPIKE_FACTOR = 2;

  @Value("${app.initialization.minLatency}")
  private int minInitializationLatecy;

  @Value("${app.initialization.maxLatency}")
  private int maxInitializationLatecy;

  @Value("${app.initialization.latencySpikeFactor}")
  private int latencySpikeFactor;

  private int getInitizalizationLatency() {
    final int baseLatency = getLatency(minInitializationLatecy, maxInitializationLatecy)*1000;
    final boolean spikeLatency = System.currentTimeMillis()%3 == 0;
    return spikeLatency? LATENCY_SPIKE_FACTOR*baseLatency : baseLatency;
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public RestHandler provideRestServlet(@Qualifier("app-ServletConfig") ServletConfig servletConfig, 
      @Qualifier("app-WebAppContext") WebApplicationContext webAppContext, RequestsQueue requestsQueue) {

    final RestServlet restServlet = new RestServlet(webAppContext);
    
    try {
      restServlet.initialize(servletConfig, webAppContext);
    } catch(Exception ex) {
      ex.printStackTrace(System.err);
    }

    final int latency = getInitizalizationLatency();
    System.out.println("initialization latency = " + Integer.toString(latency, 10));

    try {
      Thread.sleep(latency);
    } catch(Exception ex) {
      ex.printStackTrace(System.err);
    }

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
