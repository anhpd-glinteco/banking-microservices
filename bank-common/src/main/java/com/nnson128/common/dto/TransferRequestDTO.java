package com.nnson128.common.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Data
public class TransferRequestDTO {
    @NotBlank
    @Size(max = 255)
    private String fromAccountNumber;
    @NotBlank
    @Size(max = 255)
    private String toAccountNumber;
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    @Size(max = 255)
    private String description;

    @AssertTrue(message = "must differ from toAccountNumber")
    public boolean isDifferentAccounts() {
        return fromAccountNumber == null || toAccountNumber == null || !fromAccountNumber.equals(toAccountNumber);
    }
}
