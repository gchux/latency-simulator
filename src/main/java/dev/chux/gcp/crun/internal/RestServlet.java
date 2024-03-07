package dev.chux.gcp.crun.internal;

import java.io.IOException;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.MappingMatch;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import dev.chux.gcp.crun.web.RestHandler;
import dev.chux.gcp.crun.internal.app.TestRestController;

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
      
      System.out.println("RestServlet: " + request);

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
