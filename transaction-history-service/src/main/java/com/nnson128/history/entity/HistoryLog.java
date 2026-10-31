package com.nnson128.history.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_history", indexes = {
        @Index(name = "idx_history_from_account_number", columnList = "fromAccountNumber"),
        @Index(name = "idx_history_to_account_number", columnList = "toAccountNumber"),
        @Index(name = "idx_history_transaction_id", columnList = "transactionId"),
        @Index(name = "uk_history_event_id", columnList = "eventId", unique = true)
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HistoryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String transactionId;
    private String eventType;

    @Column(length = 255)
    private String eventId;

    private String fromAccountNumber;
    private String toAccountNumber;
    
    @Lob
    private String eventData; // Store JSON string of the event
    
    private LocalDateTime timestamp;
}
