package com.nnson128.account.controller;

import com.nnson128.account.entity.Loan;
import com.nnson128.account.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LoanController {
    
    private final LoanService loanService;
    
    /**
     * Apply for a new loan
     * POST /api/loans/apply
     */
    @PostMapping("/apply")
    public ResponseEntity<?> applyForLoan(@RequestBody Map<String, Object> request) {
        try {
            Long accountId = Long.valueOf(request.get("accountId").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            Integer termMonths = Integer.valueOf(request.get("termMonths").toString());
            String purpose = request.get("purpose").toString();
            
            Loan loan = loanService.applyForLoan(accountId, amount, termMonths, purpose);
            return ResponseEntity.ok(loan);
        } catch (IllegalArgumentException e) {
            log.warn("Loan application validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error applying for loan", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process loan application"));
        }
    }
    
    /**
     * Get all loans for an account
     * GET /api/loans/account/{accountId}
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Loan>> getLoansByAccount(@PathVariable Long accountId) {
        try {
            List<Loan> loans = loanService.getLoansByAccountId(accountId);
            return ResponseEntity.ok(loans);
        } catch (Exception e) {
            log.error("Error fetching loans for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get loan by ID
     * GET /api/loans/{loanId}
     */
    @GetMapping("/{loanId}")
    public ResponseEntity<?> getLoanById(@PathVariable Long loanId) {
        try {
            Loan loan = loanService.getLoanById(loanId);
            return ResponseEntity.ok(loan);
        } catch (Exception e) {
            log.error("Error fetching loan: {}", loanId, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Approve loan (admin function - auto-approve for now)
     * POST /api/loans/{loanId}/approve
     */
    @PostMapping("/{loanId}/approve")
    public ResponseEntity<?> approveLoan(@PathVariable Long loanId) {
        try {
            Loan loan = loanService.approveLoan(loanId);
            return ResponseEntity.ok(loan);
        } catch (IllegalArgumentException e) {
            log.warn("Loan approval validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error approving loan: {}", loanId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to approve loan"));
        }
    }
    
    /**
     * Make a loan repayment
     * POST /api/loans/{loanId}/repay
     */
    @PostMapping("/{loanId}/repay")
    public ResponseEntity<?> makeRepayment(@PathVariable Long loanId, @RequestBody Map<String, Object> request) {
        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            Loan loan = loanService.makeRepayment(loanId, amount);
            return ResponseEntity.ok(loan);
        } catch (IllegalArgumentException e) {
            log.warn("Loan repayment validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error making loan repayment: {}", loanId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process repayment"));
        }
    }
}
