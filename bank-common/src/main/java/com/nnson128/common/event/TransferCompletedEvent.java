package com.nnson128.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TransferCompletedEvent extends BaseEvent {
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String status; // SUCCESS
}
