package com.nnson128.account.repository;

import com.nnson128.account.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class UserProfileQueriesImpl implements UserProfileQueries {
  private final EntityManager entityManager;

  @Override
  public Page<User> findProfiles(String username, String email, Pageable pageable) {
    String filter =
        " where (:username is null or username = :username)"
            + " and (:email is null or email = :email)";
    long total =
        ((Number)
                entityManager
                    .createNativeQuery("select count(*) from users" + filter)
                    .setParameter("username", username)
                    .setParameter("email", email)
                    .getSingleResult())
            .longValue();
    if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
    String ordering =
        pageable.getSort().stream()
            .map(
                order -> {
                  String column =
                      switch (order.getProperty()) {
                        case "id" -> "id";
                        case "username" -> "username";
                        case "createdAt" -> "created_at";
                        default -> throw new IllegalArgumentException("Invalid sort field");
                      };
                  return column + (order.isAscending() ? " asc" : " desc");
                })
            .collect(Collectors.joining(", "));
    // JPA setFirstResult accepts int; native LIMIT/OFFSET preserves the full Pageable long offset.
    @SuppressWarnings("unchecked")
    List<User> content =
        entityManager
            .createNativeQuery(
                "select * from users"
                    + filter
                    + " order by "
                    + ordering
                    + " limit :size offset :offset",
                User.class)
            .setParameter("username", username)
            .setParameter("email", email)
            .setParameter("size", pageable.getPageSize())
            .setParameter("offset", pageable.getOffset())
            .getResultList();
    return new PageImpl<>(content, pageable, total);
  }
}
