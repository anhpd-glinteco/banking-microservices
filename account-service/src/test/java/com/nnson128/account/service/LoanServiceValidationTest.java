package com.nnson128.account.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nnson128.account.repository.AccountRepository;
import com.nnson128.account.repository.LoanRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanServiceValidationTest {
    private LoanRepository loans;
    private AccountRepository accounts;
    private LoanService service;

    @BeforeEach
    void setUp() {
        loans = mock(LoanRepository.class);
        accounts = mock(AccountRepository.class);
        service = new LoanService(loans, accounts);
    }

    @Test
    void invalidApplicationValuesFailBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.applyForLoan(1L, BigDecimal.ZERO, 12, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> service.applyForLoan(1L, new BigDecimal("10000000.001"), 12, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> service.applyForLoan(1L, new BigDecimal("10000000001"), 12, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> service.applyForLoan(1L, new BigDecimal("10000000"), 5, "fixture"));
        verifyNoInteractions(loans, accounts);
    }

    @Test
    void invalidRepaymentValuesFailBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.makeRepayment(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.makeRepayment(1L, new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () -> service.makeRepayment(1L, new BigDecimal("0.001")));
        verifyNoInteractions(loans, accounts);
    }
}
