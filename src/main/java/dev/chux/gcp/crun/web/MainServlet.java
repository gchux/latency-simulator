package dev.chux.gcp.crun.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.MappingMatch;

import java.io.IOException;
import java.util.concurrent.Future;
import com.google.common.util.concurrent.Futures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.ServletRequestPathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
// @WebServlet(urlPatterns={"/*"}, asyncSupported=true)
@WebServlet(asyncSupported=true)
public class MainServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MainServlet.class);

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

			ServletRequestPathUtils.parseAndCache(request);
			mapping = (mapping != null ? mapping : request.getHttpServletMapping());
			request.setAttribute(RequestDispatcher.INCLUDE_MAPPING, mapping);

			logger.info("HttpServletMapping: {}", mapping);

            final boolean isAsyncSupported = request.isAsyncSupported();

            logger.info("async: {}", isAsyncSupported);

            final Future<?> futureResponse = requestsQueue.submit(isAsyncSupported, request, response);

            if( !isAsyncSupported ) { 
                Futures.getUnchecked(futureResponse);
            }

        } catch(Exception ex) {
            ex.printStackTrace(System.out);
        }
    }

}
