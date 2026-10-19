package com.nnson128.history.repository;

import com.nnson128.history.entity.HistoryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoryRepository extends JpaRepository<HistoryLog, Long> {
    List<HistoryLog> findByTransactionId(String transactionId);
}
