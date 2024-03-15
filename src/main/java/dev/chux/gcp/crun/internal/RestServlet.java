package dev.chux.gcp.crun.internal;

import java.io.IOException;

import java.util.Enumeration;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import dev.chux.gcp.crun.web.RestHandler;

public class RestServlet extends DispatcherServlet implements RestHandler {

  public RestServlet(final WebApplicationContext webApplicationContext) {
    super(webApplicationContext);
  }

  public void initialize(final ServletConfig servletConfig, 
      final ApplicationContext applicationContext) throws Exception {
    init(servletConfig);
    initServletBean();
    initStrategies(applicationContext);
  }

  public void handle(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {

      final String method = request.getMethod();

      switch(method) {
        case "OPTIONS":
        case "options": {
          doOptions(request, response);
          return;
        }
        case "TRACE":
        case "trace": {
          doTrace(request, response);
          return;
        }
        default:
          processRequest(request, response);
      }
  }

}
