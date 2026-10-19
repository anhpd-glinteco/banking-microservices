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
public class CompensationEvent extends BaseEvent {
    // Event to refund money to sender if the credit to receiver fails
    private String fromAccountId;
    private BigDecimal amount;
    private String reason;
}
