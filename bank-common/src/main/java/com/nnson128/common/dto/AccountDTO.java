package com.nnson128.common.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AccountDTO {
    private Long id; // internal ID
    private Long userId;
    private String accountNumber;
    private String accountName; // Using String instead of Enum to decouple
    private BigDecimal balance;
}
