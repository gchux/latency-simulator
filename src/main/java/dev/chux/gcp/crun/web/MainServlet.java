package dev.chux.gcp.crun.web;

import javax.servlet.ServletException;
import javax.servlet.AsyncContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Future;
import com.google.common.util.concurrent.Futures;

import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("serial")
@WebServlet(urlPatterns={"/*"}, asyncSupported=true)
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
