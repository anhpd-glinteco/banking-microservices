package com.nnson128.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@SuperBuilder
@NoArgsConstructor
public class Transaction {
    @Id
    private String id; // UUID

    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String description;
    private String status; // PENDING, PROCESSING, SUCCESS, FAILED, COMPENSATED
    private String failureReason;
    private int retryCount = 0;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void ensureId(){
        if(this.id == null){
            this.id = UUID.randomUUID().toString();
        }
    }
}
