package com.nnson128.account.controller;

import com.nnson128.account.dto.LoanApplicationRequest;
import com.nnson128.account.dto.LoanRepaymentRequest;
import com.nnson128.account.entity.Loan;
import com.nnson128.account.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "*")
public class LoanController {
    private final LoanService loanService;

    @PostMapping("/apply")
    public ResponseEntity<Loan> applyForLoan(@NotNull @Valid @RequestBody LoanApplicationRequest request) {
        Loan loan = loanService.applyForLoan(request.getAccountId(), request.getAmount(), request.getTermMonths(), request.getPurpose());
        return ResponseEntity.ok(loan);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Loan>> getLoansByAccount(@PathVariable @Positive Long accountId) {
        return ResponseEntity.ok(loanService.getLoansByAccountId(accountId));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<Loan> getLoanById(@PathVariable @Positive Long loanId) {
        return ResponseEntity.ok(loanService.getLoanById(loanId));
    }

    @PostMapping("/{loanId}/approve")
    public ResponseEntity<Loan> approveLoan(@PathVariable @Positive Long loanId) {
        return ResponseEntity.ok(loanService.approveLoan(loanId));
    }

    @PostMapping("/{loanId}/repay")
    public ResponseEntity<Loan> makeRepayment(@PathVariable @Positive Long loanId, @NotNull @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.ok(loanService.makeRepayment(loanId, request.getAmount()));
    }
}
