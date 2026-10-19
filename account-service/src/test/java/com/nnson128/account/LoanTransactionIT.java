package com.nnson128.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.nnson128.account.entity.AccountBank;
import com.nnson128.account.entity.Loan;
import com.nnson128.account.entity.User;
import com.nnson128.account.repository.AccountRepository;
import com.nnson128.account.repository.LoanRepository;
import com.nnson128.account.repository.UserRepository;
import com.nnson128.account.service.LoanService;
import com.nnson128.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class LoanTransactionIT extends MysqlIntegrationSupport {
  @Autowired LoanService service;
  @SpyBean LoanRepository loans;
  @Autowired AccountRepository accounts;
  @Autowired UserRepository users;
  @Autowired PlatformTransactionManager manager;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;
  private TransactionTemplate transaction;

  record Fixture(Long accountId, Long loanId) {}

  record Snapshot(
      BigDecimal balance,
      Loan.LoanStatus status,
      BigDecimal remainingAmount,
      LocalDateTime approvedAt,
      LocalDateTime disbursedAt) {}

  static class FixtureFailure extends RuntimeException {
    FixtureFailure() {
      super("fixture failure after SQL flush");
    }
  }

  @BeforeEach
  void independentFixtureTransactions() {
    assertTrue(AopUtils.isAopProxy(service), "LoanService must be called through Spring proxy");
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.executeWithoutResult(
        status -> {
          loans.deleteAllInBatch();
          accounts.deleteAllInBatch();
          users.deleteAllInBatch();
        });
  }

  @Test
  void approvalCommitsBalanceAndLoanTogether() {
    Fixture fixture = seed(Loan.LoanStatus.PENDING, true);
    Snapshot before = snapshot(fixture);
    assertNull(before.approvedAt());
    assertNull(before.disbursedAt());

    service.approveLoan(fixture.loanId());

    Snapshot after = snapshot(fixture);
    assertEquals(before.balance().add(new BigDecimal("100.00")), after.balance());
    assertEquals(Loan.LoanStatus.ACTIVE, after.status());
    assertEquals(before.remainingAmount(), after.remainingAmount());
    assertNotNull(after.approvedAt());
    assertNotNull(after.disbursedAt());
    assertFalse(after.disbursedAt().isBefore(after.approvedAt()));
  }

  @Test
  void failureAfterFlushedAccountUpdateRollsBackEveryField() {
    Fixture fixture = seed(Loan.LoanStatus.PENDING, true);
    Snapshot before = snapshot(fixture);
    AtomicBoolean observedSqlUpdate = new AtomicBoolean();
    doAnswer(
            invocation -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              entityManager.flush();
              BigDecimal databaseBalance =
                  jdbc.queryForObject(
                      "select balance from accounts where id = ?",
                      BigDecimal.class,
                      fixture.accountId());
              assertEquals(before.balance().add(new BigDecimal("100.00")), databaseBalance);
              observedSqlUpdate.set(true);
              throw new FixtureFailure();
            })
        .when(loans)
        .save(argThat(loan -> loan != null && fixture.loanId().equals(loan.getId())));

    FixtureFailure failure =
        assertThrows(FixtureFailure.class, () -> service.approveLoan(fixture.loanId()));

    assertEquals("fixture failure after SQL flush", failure.getMessage());
    assertTrue(observedSqlUpdate.get(), "MySQL must see the updated account before failure");
    assertEquals(
        before, snapshot(fixture), "Independent read after rollback must match all fields");
  }

  @Test
  void missingLoanDoesNotMutateExistingFixture() {
    Fixture fixture = seed(Loan.LoanStatus.PENDING, true);
    Snapshot before = snapshot(fixture);
    assertThrows(ResourceNotFoundException.class, () -> service.approveLoan(Long.MAX_VALUE));
    assertEquals(before, snapshot(fixture));
  }

  @Test
  void missingAccountDoesNotMutateLoan() {
    Fixture fixture = seed(Loan.LoanStatus.PENDING, false);
    Snapshot before = snapshot(fixture);
    assertThrows(ResourceNotFoundException.class, () -> service.approveLoan(fixture.loanId()));
    assertEquals(before, snapshot(fixture));
  }

  @Test
  void invalidApprovalStatusDoesNotMutateData() {
    Fixture fixture = seed(Loan.LoanStatus.ACTIVE, true);
    Snapshot before = snapshot(fixture);
    assertThrows(IllegalArgumentException.class, () -> service.approveLoan(fixture.loanId()));
    assertEquals(before, snapshot(fixture));
  }

  @Test
  void repaymentCommitsPositiveAmountsAndRejectsZeroAndNegative() {
    Fixture fixture = seed(Loan.LoanStatus.PENDING, true);
    service.approveLoan(fixture.loanId());
    Snapshot before = snapshot(fixture);
    for (BigDecimal amount : new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("-1.00")}) {
      assertThrows(
          IllegalArgumentException.class, () -> service.makeRepayment(fixture.loanId(), amount));
      assertEquals(before, snapshot(fixture));
    }
    service.makeRepayment(fixture.loanId(), new BigDecimal("25.00"));
    Snapshot partial = snapshot(fixture);
    assertEquals(before.balance().subtract(new BigDecimal("25.00")), partial.balance());
    assertEquals(new BigDecimal("75.00"), partial.remainingAmount());
    assertEquals(Loan.LoanStatus.ACTIVE, partial.status());
    assertEquals(before.approvedAt(), partial.approvedAt());
    assertEquals(before.disbursedAt(), partial.disbursedAt());
    service.makeRepayment(fixture.loanId(), new BigDecimal("75.00"));
    Snapshot paid = snapshot(fixture);
    assertEquals(new BigDecimal("200.00"), paid.balance());
    assertEquals(new BigDecimal("0.00"), paid.remainingAmount());
    assertEquals(Loan.LoanStatus.PAID_OFF, paid.status());
  }

  private Fixture seed(Loan.LoanStatus loanStatus, boolean withAccount) {
    return transaction.execute(
        status -> {
          Long accountId = Long.MAX_VALUE;
          if (withAccount) {
            User user =
                users.saveAndFlush(
                    User.builder()
                        .username("loan-fixture")
                        .email("loan-fixture@example.test")
                        .password("fixture-only")
                        .build());
            AccountBank account = new AccountBank();
            account.setUser(user);
            account.setAccountNumber("TH4-LOAN");
            account.setBalance(new BigDecimal("200.00"));
            accountId = accounts.saveAndFlush(account).getId();
          }
          Loan loan =
              loans.saveAndFlush(
                  Loan.builder()
                      .accountId(accountId)
                      .loanAmount(new BigDecimal("100.00"))
                      .remainingAmount(new BigDecimal("100.00"))
                      .interestRate(new BigDecimal("5.00"))
                      .termMonths(12)
                      .purpose("TH4 transaction fixture")
                      .status(loanStatus)
                      .build());
          return new Fixture(accountId, loan.getId());
        });
  }

  private Snapshot snapshot(Fixture fixture) {
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    return transaction.execute(
        status -> {
          entityManager.clear();
          Loan loan = loans.findById(fixture.loanId()).orElseThrow();
          BigDecimal balance =
              accounts.findById(fixture.accountId()).map(AccountBank::getBalance).orElse(null);
          return new Snapshot(
              balance,
              loan.getStatus(),
              loan.getRemainingAmount(),
              loan.getApprovedAt(),
              loan.getDisbursedAt());
        });
  }
}
