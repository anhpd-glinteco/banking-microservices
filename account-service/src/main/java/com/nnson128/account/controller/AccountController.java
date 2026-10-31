package com.nnson128.account.controller;

import com.nnson128.account.entity.User;
import com.nnson128.account.dto.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import com.nnson128.account.service.AccountService;
import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.dto.CreateAccountRequest;
import com.nnson128.common.dto.LoginRequest;
import com.nnson128.common.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {
    private final AccountService accountService;

    @PostMapping
    public AccountDTO createAccount(@NotNull @Valid @RequestBody CreateAccountRequest dto) {
        return accountService.createAccount(dto);
    }
    
    @PostMapping("/users")
    public UserProfile createUser(@NotNull @Valid @RequestBody CreateUserRequest user) {
        return accountService.createUser(user);
    }

    @GetMapping("/users/{id}")
    public UserProfile getUser(@PathVariable @Positive Long id) {
        return accountService.getUser(id);
    }

    @PatchMapping("/users/{id}")
    public UserProfile updateUser(@PathVariable @Positive Long id,
            @NotNull @Valid @RequestBody UpdateUserProfileRequest request) {
        return accountService.updateUser(id, request);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable @Positive Long id) {
        accountService.deleteUser(id);
    }

    @GetMapping("/users")
    public Page<UserProfile> listUsers(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = ".*\\S.*") @Size(max = 255) String username,
            @RequestParam(required = false) @Pattern(regexp = ".*\\S.*") @Size(max = 255) String email,
            @RequestParam(defaultValue = "id,asc") @Pattern(regexp = "(id|username|createdAt),(asc|desc)") String sort) {
        return accountService.listUsers(page, size, username, email, sort);
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
