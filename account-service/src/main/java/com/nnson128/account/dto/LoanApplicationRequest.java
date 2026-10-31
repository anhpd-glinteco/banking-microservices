package com.nnson128.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class LoanApplicationRequest {
    @NotNull
    @Positive
    private Long accountId;
    @NotNull
    @DecimalMin("10000000.00")
    @DecimalMax("10000000000.00")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    @NotNull
    @Min(6)
    @Max(360)
    private Integer termMonths;
    @NotBlank
    @Size(max = 255)
    private String purpose;
}
