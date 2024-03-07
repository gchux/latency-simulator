package dev.chux.gcp.crun.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface RestHandler {

  public void handle(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

}
