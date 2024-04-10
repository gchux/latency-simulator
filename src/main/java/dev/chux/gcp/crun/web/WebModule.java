package dev.chux.gcp.crun.web;

import javax.servlet.Servlet;

import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.UpgradeProtocol;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import javax.servlet.ServletConfig;

@EnableAutoConfiguration
@ServletComponentScan
// @Configuration
@Configuration(proxyBeanMethods = false)
@ComponentScan
public class WebModule { 

  private static final String TOMCAT = TomcatWebServer.class.toString();

  @Bean("app-ServletWebServerApplicationContext")
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public ServletWebServerApplicationContext provideServletWebServerApplicationContext(
      ServletWebServerApplicationContext servletWebServerApplicationContext) {
    return servletWebServerApplicationContext;
  }

  @Bean("app-WebServer")
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public WebServer provideWebServer(
      ServletWebServerApplicationContext servletWebServerApplicationContext) {
    return servletWebServerApplicationContext.getWebServer();
  }

  // see: https://github.com/spring-projects/spring-boot/blob/v2.7.9/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/ServletWebServerFactoryConfiguration.java
  @Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Servlet.class, Tomcat.class, UpgradeProtocol.class })
	@ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
	static class EmbeddedTomcat {

    @Bean("app-WebServer-kind")
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public String provideWebServerKind() {
      return WebModule.TOMCAT;
    }

	}

}
