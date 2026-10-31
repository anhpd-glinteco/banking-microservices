package com.nnson128.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class LoanRepaymentRequest {
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
}
