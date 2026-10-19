package com.nnson128.account.repository;

import com.nnson128.account.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserProfileQueries {
  Page<User> findProfiles(String username, String email, Pageable pageable);
}
