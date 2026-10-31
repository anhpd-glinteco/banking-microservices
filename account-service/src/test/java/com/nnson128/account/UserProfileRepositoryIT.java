package com.nnson128.account;

import static org.junit.jupiter.api.Assertions.*;

import com.nnson128.account.dto.*;
import com.nnson128.account.repository.*;
import com.nnson128.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserProfileRepositoryIT extends MysqlIntegrationSupport {
  @Autowired AccountService service;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void crudFiltersAndStableDatabasePages() {
    var first =
        service.createUser(
            new CreateUserRequest("page-a", "page-a@example.test", "fixture", "Same", null, null));
    var second =
        service.createUser(
            new CreateUserRequest("page-b", "page-b@example.test", "fixture", "Same", null, null));
    assertEquals(first.id(), service.getUser(first.id()).id());
    assertEquals(
        "Updated",
        service.updateUser(first.id(), new UpdateUserProfileRequest("Updated", "123")).fullName());
    jdbc.update(
        "update users set created_at = ? where id in (?, ?)",
        java.sql.Timestamp.valueOf("2026-01-01 00:00:00"),
        first.id(),
        second.id());
    var page = service.listUsers(0, 1, null, null, "createdAt,asc");
    assertEquals(2, page.getTotalElements());
    var highPage = service.listUsers(Integer.MAX_VALUE, 100, null, null, "id,asc");
    assertTrue(highPage.isEmpty());
    assertEquals(2, highPage.getTotalElements());
    assertEquals(first.id(), page.getContent().get(0).id());
    assertEquals(
        second.id(), service.listUsers(1, 1, null, null, "createdAt,asc").getContent().get(0).id());
    assertTrue(service.listUsers(2, 1, null, null, "id,asc").isEmpty());
    assertEquals(2, service.listUsers(2, 1, null, null, "id,asc").getTotalElements());
    assertEquals(1, service.listUsers(0, 20, "page-a", null, "id,asc").getTotalElements());
    assertEquals(1, service.listUsers(0, 20, null, second.email(), "id,asc").getTotalElements());
    assertEquals(
        1, service.listUsers(0, 20, "page-a", first.email(), "id,desc").getTotalElements());
    assertEquals(
        0, service.listUsers(0, 20, "page-a", second.email(), "id,asc").getTotalElements());
    assertEquals(
        second.id(),
        service.listUsers(0, 20, null, null, "username,desc").getContent().get(0).id());
    service.deleteUser(first.id());
    service.deleteUser(second.id());
    assertThrows(
        com.nnson128.common.exception.ResourceNotFoundException.class,
        () -> service.getUser(first.id()));
    assertThrows(
        com.nnson128.common.exception.ResourceNotFoundException.class,
        () -> service.updateUser(first.id(), new UpdateUserProfileRequest("Missing", null)));
    assertThrows(
        com.nnson128.common.exception.ResourceNotFoundException.class,
        () -> service.deleteUser(first.id()));
  }
}
