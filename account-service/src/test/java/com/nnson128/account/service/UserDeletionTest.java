package com.nnson128.account.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.nnson128.account.entity.User;
import com.nnson128.account.kafka.AccountKafkaProducer;
import com.nnson128.account.repository.AccountRepository;
import com.nnson128.account.repository.UserRepository;
import com.nnson128.common.exception.DataIntegrityException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserDeletionTest {
  @Mock AccountRepository accounts;
  @Mock UserRepository users;
  @Mock AccountKafkaProducer producer;
  @Mock OtpService otp;
  @InjectMocks AccountService service;

  @Test
  void accountCreatedAfterPrecheckIsTranslatedToSafeConflict() {
    User user = User.builder().id(1L).build();
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(accounts.existsByUserId(1L)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("fixture foreign key")).when(users).flush();

    assertThrows(DataIntegrityException.class, () -> service.deleteUser(1L));
    verify(users).delete(user);
    verify(users).flush();
  }
}
