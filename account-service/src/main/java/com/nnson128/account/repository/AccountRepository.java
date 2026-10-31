package com.nnson128.account.repository;

import com.nnson128.account.entity.AccountBank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountBank, Long> {
    Optional<AccountBank> findByAccountNumber(String accountNumber);
    boolean existsByUserId(Long userId);
    Optional<AccountBank> findByUserId(Long userId);
}
