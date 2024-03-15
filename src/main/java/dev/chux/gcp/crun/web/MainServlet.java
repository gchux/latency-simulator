package dev.chux.gcp.crun.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Future;
import com.google.common.util.concurrent.Futures;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.MappingMatch;

import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("serial")
// @WebServlet(urlPatterns={"/*"}, asyncSupported=true)
@WebServlet(asyncSupported=true)
public class MainServlet extends HttpServlet {

    @Autowired
    protected RequestsQueue requestsQueue;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        handle(request, response);
    }

    protected void handle(final HttpServletRequest request, final HttpServletResponse response) {
        try {

            HttpServletMapping mapping = (HttpServletMapping) request.getAttribute(RequestDispatcher.INCLUDE_MAPPING);
			mapping = (mapping != null ? mapping : request.getHttpServletMapping());
			request.setAttribute(RequestDispatcher.INCLUDE_MAPPING, mapping);

			System.out.println("HttpServletMapping: " + mapping);

            final boolean isAsyncSupported = request.isAsyncSupported();

            System.out.println("async: " + isAsyncSupported);

            final Future<?> futureResponse = requestsQueue.submit(isAsyncSupported, request, response);

            if( !isAsyncSupported ) { 
                Futures.getUnchecked(futureResponse);
            }

        } catch(Exception ex) {
            ex.printStackTrace(System.out);
        }
    }

}
