package com.nnson128.account.service;

import com.nnson128.account.entity.AccountBank;
import com.nnson128.account.entity.Loan;
import com.nnson128.account.repository.AccountRepository;
import com.nnson128.account.repository.LoanRepository;
import com.nnson128.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {
    
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    
    /**
     * Apply for a new loan
     */
    @Transactional
    public Loan applyForLoan(Long accountId, BigDecimal amount, Integer termMonths, String purpose) {
        // Validate account exists
        AccountBank account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        // Validate loan parameters
        if (amount.compareTo(new BigDecimal("10000000")) < 0) {
            throw new IllegalArgumentException("Số tiền vay tối thiểu là 10.000.000 VND (10 triệu)");
        }
        
        if (amount.compareTo(new BigDecimal("10000000000")) > 0) {
            throw new IllegalArgumentException("Số tiền vay tối đa là 10.000.000.000 VND (10 tỷ)");
        }
        
        if (termMonths < 6 || termMonths > 360) {
            throw new IllegalArgumentException("Loan term must be between 6 and 360 months");
        }
        
        // Check for existing active loans
        List<Loan> activeLoans = loanRepository.findByAccountIdAndStatus(accountId, Loan.LoanStatus.ACTIVE);
        if (!activeLoans.isEmpty()) {
            throw new IllegalArgumentException("You already have an active loan. Please pay it off before applying for a new one.");
        }
        
        // Calculate interest rate based on amount and term (simple logic)
        BigDecimal interestRate = calculateInterestRate(amount, termMonths);
        
        // Calculate monthly payment using standard amortization formula
        BigDecimal monthlyPayment = calculateMonthlyPayment(amount, interestRate, termMonths);
        
        // Create loan application
        Loan loan = Loan.builder()
                .accountId(accountId)
                .loanAmount(amount)
                .remainingAmount(amount)
                .interestRate(interestRate)
                .termMonths(termMonths)
                .purpose(purpose)
                .status(Loan.LoanStatus.PENDING)
                .monthlyPayment(monthlyPayment)
                .build();
        
        Loan savedLoan = loanRepository.save(loan);
        log.info("Loan application created: ID={}, AccountID={}, Amount={}", 
                savedLoan.getId(), accountId, amount);
        
        return savedLoan;
    }
    
    /**
     * Get all loans for an account
     */
    public List<Loan> getLoansByAccountId(Long accountId) {
        return loanRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }
    
    /**
     * Get loan by ID
     */
    public Loan getLoanById(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    }
    
    /**
     * Approve loan and disburse funds
     */
    @Transactional
    public Loan approveLoan(Long loanId) {
        Loan loan = getLoanById(loanId);
        
        if (loan.getStatus() != Loan.LoanStatus.PENDING) {
            throw new IllegalArgumentException("Only pending loans can be approved");
        }
        
        AccountBank account = accountRepository.findById(loan.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        // Update loan status
        loan.setStatus(Loan.LoanStatus.APPROVED);
        loan.setApprovedAt(LocalDateTime.now());
        loan.setDisbursedAt(LocalDateTime.now());
        
        // Disburse funds to account
        account.setBalance(account.getBalance().add(loan.getLoanAmount()));
        accountRepository.save(account);
        
        // Mark as active
        loan.setStatus(Loan.LoanStatus.ACTIVE);
        
        Loan updatedLoan = loanRepository.save(loan);
        log.info("Loan approved and disbursed: ID={}, Amount={}", loanId, loan.getLoanAmount());
        
        return updatedLoan;
    }
    
    /**
     * Make a loan repayment
     */
    @Transactional
    public Loan makeRepayment(Long loanId, BigDecimal amount) {
        Loan loan = getLoanById(loanId);
        
        if (loan.getStatus() != Loan.LoanStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active loans can be repaid");
        }
        
        AccountBank account = accountRepository.findById(loan.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        // Check sufficient balance
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance for repayment");
        }
        
        // Check repayment amount
        if (amount.compareTo(loan.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Repayment amount exceeds remaining loan balance");
        }
        
        // Deduct from account
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        
        // Update loan
        BigDecimal newRemaining = loan.getRemainingAmount().subtract(amount);
        loan.setRemainingAmount(newRemaining);
        
        // Check if loan is paid off
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            loan.setStatus(Loan.LoanStatus.PAID_OFF);
            log.info("Loan paid off: ID={}", loanId);
        }
        
        Loan updatedLoan = loanRepository.save(loan);
        log.info("Loan repayment made: ID={}, Amount={}, Remaining={}", 
                loanId, amount, newRemaining);
        
        return updatedLoan;
    }
    
    /**
     * Calculate interest rate based on loan amount and term
     */
    private BigDecimal calculateInterestRate(BigDecimal amount, Integer termMonths) {
        // Simple tiered interest rate logic
        BigDecimal baseRate = new BigDecimal("5.0"); // 5% base rate
        
        // Lower rate for larger amounts
        if (amount.compareTo(new BigDecimal("50000")) >= 0) {
            baseRate = new BigDecimal("4.5");
        } else if (amount.compareTo(new BigDecimal("20000")) >= 0) {
            baseRate = new BigDecimal("4.75");
        }
        
        // Higher rate for longer terms
        if (termMonths > 120) {
            baseRate = baseRate.add(new BigDecimal("0.5"));
        } else if (termMonths > 60) {
            baseRate = baseRate.add(new BigDecimal("0.25"));
        }
        
        return baseRate;
    }
    
    /**
     * Calculate monthly payment using amortization formula
     * M = P * [r(1+r)^n] / [(1+r)^n - 1]
     * where: M = monthly payment, P = principal, r = monthly interest rate, n = number of payments
     */
    private BigDecimal calculateMonthlyPayment(BigDecimal principal, BigDecimal annualRate, Integer months) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            // No interest case
            return principal.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
        }
        
        // Convert annual rate to monthly rate (divide by 12 and by 100)
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP)
                                          .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        
        // Calculate (1 + r)^n
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowerN = onePlusR.pow(months);
        
        // Calculate monthly payment
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowerN);
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE);
        
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
