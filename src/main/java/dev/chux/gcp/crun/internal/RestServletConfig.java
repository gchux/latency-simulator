package dev.chux.gcp.crun.internal;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import java.util.Enumeration;
import java.util.Collections;

final class RestServletConfig implements ServletConfig {

  private final ServletContext servletContext;

  RestServletConfig(final ServletContext servletContext){
    this.servletContext = servletContext;
  }

  public ServletContext getServletContext() {
    return this.servletContext;
  }

  public String getServletName() {
    return RestServlet.class.toString();
  }

  public String getInitParameter(String name) {
    return null;
  }

  public Enumeration<String> getInitParameterNames() {
    return Collections.emptyEnumeration();
  }

}
