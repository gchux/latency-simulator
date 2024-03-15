package dev.chux.gcp.crun.internal;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import dev.chux.gcp.crun.web.RequestsQueue;
import dev.chux.gcp.crun.web.RestHandler;
import dev.chux.gcp.crun.internal.app.AppConfig;

@Configuration
//@EnableAutoConfiguration
public class RestModule {

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
