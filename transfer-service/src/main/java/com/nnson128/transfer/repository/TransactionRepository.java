package com.nnson128.transfer.repository;

import com.nnson128.transfer.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    
    @Query("SELECT t FROM Transaction t WHERE t.fromAccountNumber = :from AND t.toAccountNumber = :to " +
           "AND t.amount = :amount AND t.status IN ('PENDING', 'PROCESSING') " +
           "ORDER BY t.createdAt DESC")
    Transaction findPendingOrProcessingTransaction(
        @Param("from") String fromAccountNumber,
        @Param("to") String toAccountNumber, 
        @Param("amount") BigDecimal amount
    );
}
