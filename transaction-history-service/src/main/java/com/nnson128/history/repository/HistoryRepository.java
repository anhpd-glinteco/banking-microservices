package com.nnson128.history.repository;

import com.nnson128.history.entity.HistoryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface HistoryRepository extends JpaRepository<HistoryLog, Long> {
    List<HistoryLog> findByTransactionId(String transactionId);

    boolean existsByEventId(String eventId);

    @Query("select distinct h from HistoryLog h where h.fromAccountNumber = :number " +
            "or h.toAccountNumber = :number or (h.eventType = 'TRANSFER_FAILED' " +
            "and h.fromAccountNumber is null and h.toAccountNumber is null " +
            "and h.transactionId is not null and exists (select owner.id from HistoryLog owner " +
            "where owner.transactionId = h.transactionId and " +
            "(owner.fromAccountNumber = :number or owner.toAccountNumber = :number))) " +
            "order by h.timestamp desc")
    List<HistoryLog> findOwnedByAccountNumber(@Param("number") String accountNumber);
}
