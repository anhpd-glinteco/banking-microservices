package com.nnson128.common.event;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
public class BaseEvent {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private String transactionId; // Key for correlation
    private String eventType;
}
