package com.nnson128.account.dto;

import com.nnson128.account.entity.User;
import java.time.LocalDateTime;

public record UserProfile(
    Long id,
    String username,
    String email,
    String fullName,
    String phone,
    String identificationNumber,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public static UserProfile from(User user) {
    return new UserProfile(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getIdentificationNumber(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
