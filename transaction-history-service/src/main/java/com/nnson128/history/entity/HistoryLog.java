package com.nnson128.history.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_history")
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
    
    @Lob
    private String eventData; // Store JSON string of the event
    
    private LocalDateTime timestamp;
}
