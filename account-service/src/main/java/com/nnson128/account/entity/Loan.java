package com.nnson128.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingAmount;
    
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate; // Annual interest rate (e.g., 5.5 for 5.5%)
    
    @Column(nullable = false)
    private Integer termMonths; // Loan term in months
    
    @Column(nullable = false)
    private String purpose; // Purpose of the loan
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LoanStatus status;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyPayment;
    
    private LocalDateTime approvedAt;
    private LocalDateTime disbursedAt;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    public enum LoanStatus {
        PENDING,      // Waiting for approval
        APPROVED,     // Approved but not disbursed
        ACTIVE,       // Loan disbursed and active
        PAID_OFF,     // Fully repaid
        REJECTED,     // Application rejected
        DEFAULTED     // Loan in default
    }
}
