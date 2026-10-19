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
public class BalanceChangedEvent extends BaseEvent {
    private String accountId;
    private BigDecimal newBalance;
    private String email;
    private String accountName;
    private String transactionType; // DEBIT or CREDIT
    private BigDecimal transactionAmount;
    private String otherPartyName;
    private String otherPartyAccount;
    private String description;
}
