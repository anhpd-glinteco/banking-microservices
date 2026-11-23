package com.nnson128.account.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class Th7InstanceTraceFilterTest {
    private static final String REQUEST_ID = "6c3e797b-ff76-40d7-9308-eb7b655f5504";

    @Test
    void echoesTheAccountInstanceForRequestsWithACanonicalTraceId() throws ServletException, IOException {
        var filter = new Th7InstanceTraceFilter("account-service-a");
        var request = new MockHttpServletRequest("GET", "/api/accounts/number/fixture");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        request.addHeader("X-TH7-Request-Id", REQUEST_ID);

        filter.doFilter(request, response, chain);

        assertEquals(REQUEST_ID, response.getHeader("X-TH7-Request-Id"));
        assertEquals("account-service-a", response.getHeader("X-TH7-Instance-Id"));
        assertSame(request, chain.getRequest());
    }

    @Test
    void neverEchoesAnInvalidTraceId() throws ServletException, IOException {
        var filter = new Th7InstanceTraceFilter("account-service-a");
        var request = new MockHttpServletRequest("GET", "/api/accounts/number/fixture");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        request.addHeader("X-TH7-Request-Id", "not-a-uuid\r\nAuthorization: Bearer forged");

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader("X-TH7-Request-Id"));
        assertNull(response.getHeader("X-TH7-Instance-Id"));
        assertSame(request, chain.getRequest());
    }
}
