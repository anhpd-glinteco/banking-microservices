package com.nnson128.account.dto;

import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @NotBlank @Size(max = 255) String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 255) String password,
    @Size(max = 255) String fullName,
    @Size(max = 255) String phone,
    @Size(max = 255) String identificationNumber) {}
