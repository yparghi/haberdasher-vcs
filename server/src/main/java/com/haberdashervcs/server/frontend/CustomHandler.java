package com.haberdashervcs.server.frontend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;


public interface CustomHandler {

    boolean matches(String op);

    void handle(
            String op,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception;

}
