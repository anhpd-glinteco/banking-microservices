package com.nnson128.account.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nnson128.account.service.AccountService;
import com.nnson128.account.service.LoanService;
import com.nnson128.account.entity.Loan;
import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.dto.CreateAccountRequest;
import com.nnson128.common.exception.GlobalExceptionHandler;
import com.nnson128.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

@WebMvcTest({AccountController.class, LoanController.class})
@Import(GlobalExceptionHandler.class)
class LoanValidationTest {
    @Autowired MockMvc mvc;
    @MockBean AccountService accountService;
    @MockBean LoanService loanService;

    @Test
    void validLoanApplicationKeepsTheLoanResponseShape() throws Exception {
        when(loanService.applyForLoan(1L, new java.math.BigDecimal("10000000"), 12, "fixture"))
                .thenReturn(Loan.builder().id(5L).accountId(1L).loanAmount(new java.math.BigDecimal("10000000"))
                        .remainingAmount(new java.math.BigDecimal("10000000")).termMonths(12).purpose("fixture")
                        .status(Loan.LoanStatus.PENDING).build());
        mvc.perform(post("/api/loans/apply").contentType("application/json")
                        .content("{\"accountId\":1,\"amount\":10000000,\"termMonths\":12,\"purpose\":\"fixture\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void invalidLoanRequestsReturnSafe400WithoutCallingService() throws Exception {
        mvc.perform(post("/api/loans/apply").contentType("application/json")
                        .content("{\"accountId\":1,\"amount\":0,\"termMonths\":12,\"purpose\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.rejectedValue").doesNotExist());
        mvc.perform(post("/api/loans/apply").contentType("application/json")
                        .content("{\"accountId\":1,\"amount\":10000000,\"termMonths\":361,\"purpose\":\"test\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/loans/1/repay").contentType("application/json").content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/loans/apply").contentType("application/json").content("null"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/loans/apply").contentType("application/json").content("{\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
        verifyNoInteractions(loanService);
    }

    @Test
    void invalidAccountCreateIsRejectedAndIdCannotBeUsedToWrite() throws Exception {
        mvc.perform(post("/api/accounts").contentType("application/json")
                        .content("{\"id\":99,\"userId\":0,\"accountNumber\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts").contentType("application/json").content("null"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accountService);

        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(
                AccountDTO.builder().id(7L).userId(1L).accountNumber("TH4-A").build());
        mvc.perform(post("/api/accounts").contentType("application/json")
                        .content("{\"id\":99,\"userId\":1,\"accountNumber\":\"TH4-A\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        ArgumentCaptor<CreateAccountRequest> request = ArgumentCaptor.forClass(CreateAccountRequest.class);
        verify(accountService).createAccount(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("TH4-A", request.getValue().getAccountNumber());
    }

    @Test
    void missingRequestParameterReturnsAFieldNamed400() throws Exception {
        mvc.perform(post("/api/accounts/forgot-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: is required"));
        verifyNoInteractions(accountService);
    }

    @Test
    void notFoundAndUnexpectedErrorsKeepTheirStatusAndSafeMessage() throws Exception {
        when(loanService.getLoanById(88L)).thenThrow(new ResourceNotFoundException("secret 88"));
        mvc.perform(get("/api/loans/88")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Requested resource was not found"));
        when(loanService.getLoanById(89L)).thenThrow(new IllegalStateException("secret database detail"));
        mvc.perform(get("/api/loans/89")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An internal server error occurred."));
    }
}
