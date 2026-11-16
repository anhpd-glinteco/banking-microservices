package com.nnson128.history;

import com.nnson128.history.config.SecurityConfig;
import com.nnson128.history.controller.HistoryController;
import com.nnson128.history.service.HistoryService;
import com.nnson128.common.exception.GlobalExceptionHandler;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {HistoryController.class, SecurityConfig.class, GlobalExceptionHandler.class, SecurityBoundaryTest.HealthStub.class})
class SecurityBoundaryTest {
    @org.springframework.web.bind.annotation.RestController
    static class HealthStub {
        @org.springframework.web.bind.annotation.GetMapping("/actuator/health")
        java.util.Map<String, String> health() { return java.util.Map.of("status", "UP"); }
    }

    @Autowired MockMvc mvc;
    @MockBean HistoryService service;
    @MockBean JwtDecoder decoder;
    @MockBean RSAPublicKey publicKey;

    @Test
    void directServiceRejectsAnonymousAndInvalidBearerBeforeBusinessCode() throws Exception {
        mvc.perform(get("/api/history/number/001")).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get("/api/history/number/001").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void validBearerReachesBusinessBoundary() throws Exception {
        when(decoder.decode("valid")).thenReturn(Jwt.withTokenValue("valid").header("alg", "RS256")
                .subject("1").claim("roles", List.of("USER")).build());
        mvc.perform(get("/api/history/number/001").header("Authorization", "Bearer valid")).andExpect(status().isOk());
        verify(service).forAccountNumber("001");
    }

    @Test
    void browserPreflightUsesSharedCorsPolicy() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/history/number/001")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        verifyNoInteractions(service);
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(service);
    }
}
