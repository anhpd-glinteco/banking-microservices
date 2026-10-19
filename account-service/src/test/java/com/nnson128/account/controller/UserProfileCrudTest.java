package com.nnson128.account.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nnson128.account.dto.*;
import com.nnson128.account.service.AccountService;
import com.nnson128.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class UserProfileCrudTest {
  @Autowired MockMvc mvc;
  @MockBean AccountService service;

  @Test
  void safeProfileAndCrudStatuses() throws Exception {
    var profile =
        new UserProfile(1L, "fixture", "fixture@example.test", "Fixture", null, null, null, null);
    when(service.createUser(any())).thenReturn(profile);
    when(service.getUser(1L)).thenReturn(profile);
    when(service.updateUser(eq(1L), any())).thenReturn(profile);
    mvc.perform(
            post("/api/accounts/users")
                .contentType("application/json")
                .content(
                    "{\"username\":\"fixture\",\"email\":\"fixture@example.test\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(get("/api/accounts/users/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(
            patch("/api/accounts/users/1")
                .contentType("application/json")
                .content("{\"fullName\":\"Fixture\"}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/accounts/users/1")).andExpect(status().isNoContent());
  }

  @Test
  void rejectsInvalidBodiesAndPagination() throws Exception {
    for (String query :
        new String[] {
          "page=-1", "page=2147483648", "size=0", "size=101", "sort=password,asc", "sort=id,up"
        }) mvc.perform(get("/api/accounts/users?" + query)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/accounts/users").param("username", " "))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/accounts/users").param("email", " ")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/accounts/users/0")).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/accounts/users")
                .contentType("application/json")
                .content("{\"username\":\" \",\"email\":\"bad\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/accounts/users/1").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/accounts/users").contentType("application/json").content("null"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/accounts/users")
                .contentType("application/json")
                .content(
                    "{\"username\":\""
                        + "x".repeat(256)
                        + "\",\"email\":\"a@example.test\",\"password\":\"secret\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void missingAndRelatedUsersReturnSafeErrors() throws Exception {
    when(service.getUser(88L)).thenThrow(new ResourceNotFoundException("fixture"));
    doThrow(new DataIntegrityException("sensitive SQL")).when(service).deleteUser(1L);
    mvc.perform(get("/api/accounts/users/88")).andExpect(status().isNotFound());
    mvc.perform(delete("/api/accounts/users/1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
  }
}
