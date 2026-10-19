package com.nnson128.account.dto;

import jakarta.validation.constraints.*;

public record UpdateUserProfileRequest(
    @NotBlank @Size(max = 255) String fullName, @Size(max = 255) String phone) {}
