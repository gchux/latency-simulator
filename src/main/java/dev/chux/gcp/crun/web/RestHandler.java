package dev.chux.gcp.crun.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface RestHandler {

  public void handle(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

}
