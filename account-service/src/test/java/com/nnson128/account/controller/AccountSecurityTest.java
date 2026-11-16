package com.nnson128.account.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.nnson128.account.dto.*;
import com.nnson128.account.security.AccountSecurityConfiguration;
import com.nnson128.account.service.*;
import com.nnson128.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest({AccountController.class, LoanController.class})
@Import({AccountSecurityConfiguration.class, GlobalExceptionHandler.class})
class AccountSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AccountService accounts;
    @MockBean LoanService loans;
    @MockBean java.security.interfaces.RSAPublicKey jwtPublicKey;
    @MockBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test void anonymousAndNonAdminRequestsAreRejectedBeforeBusinessLogic() throws Exception {
        mvc.perform(get("/api/accounts/1")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/accounts/users").with(jwt().jwt(j -> j.subject("1"))).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/loans/1/approve").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts, loans);
    }
    @Test void allowedBrowserOriginReceivesCorsHeadersOn401AndPreflight() throws Exception {
        mvc.perform(get("/api/accounts/1").header("Origin", "http://127.0.0.1:5174"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5174"));
        mvc.perform(options("/api/accounts/1").header("Origin", "http://127.0.0.1:5174")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5174"));
    }
    @Test void ownProfileAndLookupAreSafeAndCrossUserProfileIsForbidden() throws Exception {
        when(accounts.getUser(1L)).thenReturn(new UserProfile(1L, "fixture", "fixture@example.test", "Fixture", null, null, null, null));
        mvc.perform(get("/api/accounts/users/1").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/accounts/users/2").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verify(accounts, never()).getUser(2L);
        when(accounts.lookup("other")).thenReturn(new AccountLookup("other", "Other User"));
        mvc.perform(get("/api/accounts/lookup/other").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountNumber").value("other"))
                .andExpect(jsonPath("$.accountName").value("Other User"))
                .andExpect(jsonPath("$.balance").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist());
    }
    @Test void loanApplyAndRepaymentStopBeforeMutationOnOwnerDenial() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden")).when(accounts).requireAccountOwner(2L, false);
        mvc.perform(post("/api/loans/apply").with(jwt().jwt(j -> j.subject("1")))
                .contentType("application/json").content("{\"accountId\":2,\"amount\":10000000,\"termMonths\":12,\"purpose\":\"fixture\"}"))
                .andExpect(status().isForbidden());
        when(loans.getLoanById(3L)).thenReturn(com.nnson128.account.entity.Loan.builder().id(3L).accountId(2L).build());
        mvc.perform(post("/api/loans/3/repay").with(jwt().jwt(j -> j.subject("1")))
                .contentType("application/json").content("{\"amount\":100}"))
                .andExpect(status().isForbidden());
        verify(loans, never()).applyForLoan(any(), any(), any(), any());
        verify(loans, never()).makeRepayment(any(), any());
    }
}
