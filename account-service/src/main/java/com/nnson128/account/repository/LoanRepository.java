package com.nnson128.account.repository;

import com.nnson128.account.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<Loan> findByAccountIdAndStatus(Long accountId, Loan.LoanStatus status);
}
