package com.nnson128.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnson128.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public final class SecurityErrors {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private SecurityErrors() {}
    public static void write401(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, 401, "Unauthorized", "Authentication required");
    }
    public static void write403(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        write(request, response, 403, "Forbidden", "Access denied");
    }
    private static void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        JSON.writeValue(response.getOutputStream(), new ErrorResponse(LocalDateTime.now(), status, error, message, request.getRequestURI()));
    }
}
