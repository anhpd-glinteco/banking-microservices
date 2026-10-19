package com.nnson128.common.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String fullName;
    private String username;
    private String password;
    private String email;
    private String phoneNumber;
    private String identificationNumber;
    private String otp;
}
