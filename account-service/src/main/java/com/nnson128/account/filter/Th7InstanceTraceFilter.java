package com.nnson128.account.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "th7.trace.enabled", havingValue = "true")
@Slf4j
public class Th7InstanceTraceFilter extends OncePerRequestFilter {
    private static final Pattern REQUEST_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final String instanceId;

    public Th7InstanceTraceFilter(@Value("${TH7_INSTANCE_ID:account-service}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-TH7-Request-Id");
        String path = request.getRequestURI();
        boolean accountRead = "GET".equals(request.getMethod())
                && (path.startsWith("/api/accounts/number/") || path.startsWith("/api/accounts/lookup/"));

        if (!accountRead || requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-TH7-Request-Id", requestId);
        response.setHeader("X-TH7-Instance-Id", instanceId);
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("TH7 account request instance={} request={} status={}",
                    instanceId, requestId, response.getStatus());
        }
    }
}
