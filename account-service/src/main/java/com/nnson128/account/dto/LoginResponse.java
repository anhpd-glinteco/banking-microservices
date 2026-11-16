package com.nnson128.account.dto;
import com.nnson128.common.dto.AccountDTO;
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String role, AccountDTO account) {}
