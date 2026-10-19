package com.nnson128.account.controller;

import com.nnson128.account.entity.User;
import com.nnson128.account.service.AccountService;
import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.dto.LoginRequest;
import com.nnson128.common.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @PostMapping
    public AccountDTO createAccount(@RequestBody AccountDTO dto) {
        return accountService.createAccount(dto);
    }
    
    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return accountService.createUser(user);
    }

    @GetMapping("/{id}")
    public AccountDTO getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }
    
    @GetMapping("/number/{accountNumber}")
    public AccountDTO getAccountByNumber(@PathVariable String accountNumber) {
        return accountService.getAccountByNumber(accountNumber);
    }
    
    @PostMapping("/login")
    public AccountDTO login(@RequestBody LoginRequest request) {
        return accountService.login(request.getUsername(), request.getPassword());
    }

    @PostMapping("/otp/generate")
    public void generateOtp(@RequestParam String email) {
        accountService.generateOtp(email);
    }

    @PostMapping("/users/register-with-otp")
    public User registerWithOtp(@RequestBody RegisterRequest request) {
        return accountService.registerWithOtp(request);
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@RequestParam String email) {
        accountService.forgotPassword(email);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@RequestParam String email, @RequestParam String otp, @RequestParam String newPassword) {
        accountService.resetPassword(email, otp, newPassword);
    }

    @GetMapping("/check-exists")
    public java.util.Map<String, Boolean> checkUserExists(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String identificationNumber) {
        return accountService.checkUserExists(email, phone, identificationNumber);
    }
}
