package com.nnson128.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.nnson128.account.dto.CreateUserRequest;
import com.nnson128.account.entity.*;
import com.nnson128.account.repository.*;
import com.nnson128.account.kafka.AccountKafkaProducer;
import com.nnson128.account.security.TokenService;
import com.nnson128.common.dto.RegisterRequest;
import com.nnson128.common.exception.InvalidCredentialsException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AccountPasswordSecurityTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final OtpService otp = mock(OtpService.class);
    final TokenService tokens = mock(TokenService.class);
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    final AccountService service = new AccountService(passwords, tokens, accounts, users, mock(AccountKafkaProducer.class), otp);

    @Test void allPasswordWritesHashAndPlaintextLoginIsRejected() {
        when(users.saveAndFlush(any())).thenAnswer(call -> { User user = call.getArgument(0); user.setId(1L); return user; });
        service.createUser(new CreateUserRequest("fixture", "fixture@example.test", "secret", "Fixture", null, null));
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertTrue(passwords.matches("secret", saved.getValue().getPassword()));
        assertEquals(User.Role.USER, saved.getValue().getRole());
        User user = User.builder().id(1L).username("old").password("secret").build();
        when(users.findByUsername("old")).thenReturn(Optional.of(user));
        assertThrows(InvalidCredentialsException.class, () -> service.login("old", "secret"));
        verifyNoInteractions(tokens);
        when(users.findByEmail("fixture@example.test")).thenReturn(Optional.of(user));
        when(otp.validateOtp("fixture@example.test", "123456")).thenReturn(true);
        service.resetPassword("fixture@example.test", "123456", "new-secret");
        assertTrue(passwords.matches("new-secret", user.getPassword()));
        RegisterRequest request = new RegisterRequest();
        request.setEmail("fixture@example.test"); request.setOtp("123456"); request.setUsername("new"); request.setPassword("register-secret");
        when(users.save(any())).thenAnswer(call -> { User created = call.getArgument(0); created.setId(2L); return created; });
        service.registerWithOtp(request);
        verify(users, times(2)).save(saved.capture());
        assertTrue(passwords.matches("register-secret", saved.getValue().getPassword()));
        assertEquals(User.Role.USER, saved.getValue().getRole());
    }

    @Test void ownerCheckDeniesEvenAdminWhenOwnerIsRequired() {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("1", "", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            AccountBank account = new AccountBank(); account.setId(9L); account.setUser(User.builder().id(2L).build());
            when(accounts.findById(9L)).thenReturn(Optional.of(account));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.requireAccountOwner(9L, false));
            assertDoesNotThrow(() -> service.requireAccountOwner(9L, true));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
