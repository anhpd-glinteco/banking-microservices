package com.nnson128.common.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateAccountRequest {
    @NotNull
    @Positive
    private Long userId;
    @NotBlank
    @Size(max = 255)
    private String accountNumber;
    @Size(max = 255)
    private String accountName;
    @Digits(integer = 13, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal balance;
}
