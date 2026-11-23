package com.nnson128.account.service;

import com.nnson128.account.entity.AccountBank;
import com.nnson128.account.entity.User;
import com.nnson128.account.kafka.AccountKafkaProducer;
import com.nnson128.account.repository.AccountRepository;
import com.nnson128.account.repository.UserRepository;
import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.dto.CreateAccountRequest;
import com.nnson128.common.dto.RegisterRequest;
import com.nnson128.common.event.*;
import com.nnson128.common.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.nnson128.account.dto.*;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.nnson128.account.security.TokenService tokenService;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountKafkaProducer kafkaProducer;

    @Transactional
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        // Debit Logic
        try {
            AccountBank account = accountRepository.findByAccountNumber(event.getFromAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            if (account.getBalance().compareTo(event.getAmount()) < 0) {
                throw new RuntimeException("Insufficient balance");
            }

            account.setBalance(account.getBalance().subtract(event.getAmount()));
            accountRepository.save(account);

            // Get recipient info
            AccountBank recipientAccount = accountRepository.findByAccountNumber(event.getToAccountId()).orElse(null);
            String recipientName = recipientAccount != null ? recipientAccount.getAccountName() : "Unknown";

            // Publish MoneyDeductedEvent
            MoneyDeductedEvent deductedEvent = MoneyDeductedEvent.builder()
                    .transactionId(event.getTransactionId())
                    .fromAccountId(event.getFromAccountId())
                    .toAccountId(event.getToAccountId())
                    .amount(event.getAmount())
                    .description(event.getDescription())
                    .eventType("MONEY_DEDUCTED")
                    .build();
            kafkaProducer.sendEvent("banking.transaction", deductedEvent);
            
            // Publish Balance Changed for SENDER (DEBIT)
            kafkaProducer.sendEvent("banking.account", BalanceChangedEvent.builder()
                .transactionId(event.getTransactionId())
                .accountId(account.getAccountNumber())
                .newBalance(account.getBalance())
                .email(account.getUser().getEmail())
                .accountName(account.getAccountName())
                .transactionType("DEBIT")
                .transactionAmount(event.getAmount())
                .otherPartyName(recipientName)
                .otherPartyAccount(event.getToAccountId())
                .description(event.getDescription())
                .build());

        } catch (Exception e) {
            log.error("Transfer failed at debit stage ({})", e.getClass().getSimpleName());
            kafkaProducer.sendEvent("banking.transaction", TransferFailedEvent.builder()
                    .transactionId(event.getTransactionId())
                    .reason(e.getMessage())
                    .build());
        }
    }

    @Transactional
    public void handleMoneyDeducted(MoneyDeductedEvent event) {
        // Credit Logic
        try {
            AccountBank account = accountRepository.findByAccountNumber(event.getToAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination Account not found"));

            account.setBalance(account.getBalance().add(event.getAmount()));
            accountRepository.save(account);

            // Get sender info
            AccountBank senderAccount = accountRepository.findByAccountNumber(event.getFromAccountId()).orElse(null);
            String senderName = senderAccount != null ? senderAccount.getAccountName() : "Unknown";

            // Publish TransferCompletedEvent
            kafkaProducer.sendEvent("banking.transaction", TransferCompletedEvent.builder()
                    .transactionId(event.getTransactionId())
                    .fromAccountId(event.getFromAccountId())
                    .toAccountId(event.getToAccountId())
                    .amount(event.getAmount())
                    .status("SUCCESS")
                    .build());
            
             // Publish Balance Changed for RECEIVER (CREDIT)
            kafkaProducer.sendEvent("banking.account", BalanceChangedEvent.builder()
                .transactionId(event.getTransactionId())
                .accountId(account.getAccountNumber())
                .newBalance(account.getBalance())
                .email(account.getUser().getEmail())
                .accountName(account.getAccountName())
                .transactionType("CREDIT")
                .transactionAmount(event.getAmount())
                .otherPartyName(senderName)
                .otherPartyAccount(event.getFromAccountId())
                .description(event.getDescription())
                .build());

        } catch (Exception e) {
            log.error("Transfer failed at credit stage ({})", e.getClass().getSimpleName());
            // Fail -> Compensate Sender
            kafkaProducer.sendEvent("banking.transaction", CompensationEvent.builder()
                    .transactionId(event.getTransactionId())
                    .fromAccountId(event.getFromAccountId())
                    .amount(event.getAmount())
                    .reason("Credit failed: " + e.getMessage())
                    .eventType("COMPENSATION")
                    .build());
             kafkaProducer.sendEvent("banking.transaction", TransferFailedEvent.builder()
                    .transactionId(event.getTransactionId())
                    .reason("Credit failed: " + e.getMessage())
                    .build());
        }
    }

    @Transactional
    public void handleCompensation(CompensationEvent event) {
        // Refund Logic
        try {
            AccountBank account = accountRepository.findByAccountNumber(event.getFromAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found for refund"));

            account.setBalance(account.getBalance().add(event.getAmount()));
            accountRepository.save(account);
            
            log.info("Compensation applied.");

             // Publish Balance Changed
            kafkaProducer.sendEvent("banking.account", BalanceChangedEvent.builder()
                .accountId(account.getAccountNumber())
                .newBalance(account.getBalance())
                .email(account.getUser().getEmail())
                .accountName(account.getAccountName())
                .build());

        } catch (Exception e) {
            log.error("Compensation failed ({}). Manual intervention required.", e.getClass().getSimpleName());
        }
    }

    // CRUD for creating accounts (simplified)
    public AccountDTO createAccount(CreateAccountRequest dto) {
        User user = userRepository.findById(dto.getUserId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        AccountBank account = new AccountBank();
        account.setUser(user);
        account.setAccountNumber(dto.getAccountNumber());
        account.setBalance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO);
        account.setAccountName(dto.getAccountName());
        accountRepository.save(account);
        return AccountDTO.builder()
                .id(account.getId())
                .userId(dto.getUserId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .balance(account.getBalance())
                .build();
    }
    
    @Transactional
    public UserProfile createUser(CreateUserRequest request) {
        User user = User.builder()
                .username(request.username()).email(request.email()).password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName()).phone(request.phone())
                .identificationNumber(request.identificationNumber()).build();
        try {
            return UserProfile.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new DataIntegrityException("User conflicts with existing data", ex);
        }
    }

    @Transactional(readOnly = true)
    public UserProfile getUser(Long id) {
        return UserProfile.from(requireUser(id));
    }

    @Transactional
    public UserProfile updateUser(Long id, UpdateUserProfileRequest request) {
        User user = requireUser(id);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        return UserProfile.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = requireUser(id);
        if (accountRepository.existsByUserId(id)) throw new DataIntegrityException("User has accounts");
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DataIntegrityException("User has related data", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<UserProfile> listUsers(int page, int size, String username, String email, String sort) {
        String[] order = sort.split(",");
        if (page < 0 || size < 1 || size > 100 || order.length != 2
                || !java.util.Set.of("id", "username", "createdAt").contains(order[0])
                || !java.util.Set.of("asc", "desc").contains(order[1])) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        Sort ordering = Sort.by(Sort.Direction.fromString(order[1]), order[0]);
        if (!order[0].equals("id")) {
            ordering = ordering.and(Sort.by(Sort.Direction.fromString(order[1]), "id"));
        }
        return userRepository.findProfiles(username, email, PageRequest.of(page, size, ordering)).map(UserProfile::from);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public void requireAccountOwner(Long id, boolean allowAdmin) {
        AccountBank account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (allowAdmin) com.nnson128.common.security.SecurityIdentity.requireOwnerOrAdmin(account.getUser().getId());
        else com.nnson128.common.security.SecurityIdentity.requireOwner(account.getUser().getId());
    }

    public AccountLookup lookup(String number) {
        AccountBank account = accountRepository.findByAccountNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return new AccountLookup(account.getAccountNumber(), account.getAccountName());
    }

    public AccountDTO getAccount(Long id) {
        AccountBank account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        return AccountDTO.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .balance(account.getBalance())
                .userId(account.getUser().getId())
                .build();
    }

    public AccountDTO getAccountByNumber(String accountNumber) {
        AccountBank account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        return AccountDTO.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .balance(account.getBalance())
                .userId(account.getUser().getId())
                .build();
    }

    private final OtpService otpService;

    public void generateOtp(String email) {
        otpService.generateOtp(email);
    }

    @Transactional
    public UserProfile registerWithOtp(RegisterRequest request) {
        if (!otpService.validateOtp(request.getEmail(), request.getOtp())) {
            throw new InvalidOtpException("Invalid OTP");
        }
        
        if (userRepository.existsByUsername(request.getUsername())) {
             throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
             throw new DuplicateResourceException("Email already exists");
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhoneNumber())
                .identificationNumber(request.getIdentificationNumber())
                .build();
                
        user = userRepository.save(user);
        
        // Create default account
        AccountBank account = new AccountBank();
        account.setUser(user);
        account.setAccountNumber(generateAccountNumber()); // Need this helper method
        account.setBalance(BigDecimal.ZERO);
        account.setAccountName(user.getFullName());
        accountRepository.save(account);
        
        return UserProfile.from(user);
    }

    private String generateAccountNumber() {
        return String.valueOf(System.currentTimeMillis()).substring(3); // Simple generation
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        
        if (password == null || user.getPassword() == null || !user.getPassword().matches("^\\$2[aby]\\$.*") || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        AccountBank account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user"));

        AccountDTO dto = AccountDTO.builder().id(account.getId()).accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName()).balance(account.getBalance()).userId(user.getId()).build();
        return new LoginResponse(tokenService.issue(user), "Bearer", 900, user.getRole().name(), dto);
    }

    public void forgotPassword(String email) {
        // Verify email exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        
        // Generate OTP and send via Kafka
        otpService.generateOtp(email);
        
        log.info("Password reset OTP event published.");
    }

    public void resetPassword(String email, String otp, String newPassword) {
        // Verify email exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        
        // Validate OTP
        if (!otpService.validateOtp(email, otp)) {
            throw new InvalidOtpException("Invalid or expired OTP");
        }
        
        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Password reset completed.");
    }

    public java.util.Map<String, Boolean> checkUserExists(String email, String phone, String identificationNumber) {
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        
        if (email != null && !email.isEmpty()) {
            result.put("emailExists", userRepository.existsByEmail(email));
        }
        
        if (phone != null && !phone.isEmpty()) {
            result.put("phoneExists", userRepository.existsByPhone(phone));
        }
        
        if (identificationNumber != null && !identificationNumber.isEmpty()) {
            result.put("identificationNumberExists", userRepository.existsByIdentificationNumber(identificationNumber));
        }
        
        return result;
    }
}
