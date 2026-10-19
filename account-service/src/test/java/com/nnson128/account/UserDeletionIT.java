package com.nnson128.account;

import static org.junit.jupiter.api.Assertions.*;

import com.nnson128.account.dto.*;
import com.nnson128.account.entity.*;
import com.nnson128.account.repository.*;
import com.nnson128.account.service.AccountService;
import com.nnson128.common.exception.DataIntegrityException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserDeletionIT extends MysqlIntegrationSupport {
  @Autowired AccountService service;
  @Autowired UserRepository users;
  @Autowired AccountRepository accounts;
  @Autowired LoanRepository loans;
  @Autowired PlatformTransactionManager manager;

  @Test
  void protectsFinancialDataAndForeignKeyWithoutCascade() {
    var user =
        service.createUser(
            new CreateUserRequest("owned", "owned@example.test", "fixture", null, null, null));
    var tx = new TransactionTemplate(manager);
    Long accountId =
        tx.execute(
            status -> {
              var account = new AccountBank();
              account.setUser(users.findById(user.id()).orElseThrow());
              account.setAccountNumber("TH4-DELETE");
              account.setBalance(new BigDecimal("125.00"));
              return accounts.saveAndFlush(account).getId();
            });
    Long loanId =
        tx.execute(
            status ->
                loans
                    .saveAndFlush(
                        Loan.builder()
                            .accountId(accountId)
                            .loanAmount(new BigDecimal("50.00"))
                            .remainingAmount(new BigDecimal("50.00"))
                            .interestRate(new BigDecimal("5.00"))
                            .termMonths(12)
                            .purpose("Fixture")
                            .status(Loan.LoanStatus.PENDING)
                            .build())
                    .getId());
    assertThrows(DataIntegrityException.class, () -> service.deleteUser(user.id()));
    assertTrue(users.existsById(user.id()));
    assertEquals(new BigDecimal("125.00"), accounts.findById(accountId).orElseThrow().getBalance());
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            tx.executeWithoutResult(
                status -> {
                  users.deleteById(user.id());
                  users.flush();
                }));
    assertTrue(users.existsById(user.id()));
    assertTrue(accounts.existsById(accountId));
    assertTrue(loans.existsById(loanId));
    tx.executeWithoutResult(
        status -> {
          loans.deleteById(loanId);
          loans.flush();
          accounts.deleteById(accountId);
          accounts.flush();
        });
    service.deleteUser(user.id());
    assertFalse(users.existsById(user.id()));
  }
}
