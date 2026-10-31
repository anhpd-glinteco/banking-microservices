package com.nnson128.history.controller;

import com.nnson128.history.service.HistoryService.BadHistoryRequestException;
import com.nnson128.history.service.HistoryService.HistoryAccountNotFoundException;
import com.nnson128.history.service.HistoryService.HistoryDependencyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class HistoryExceptionHandler {
    @ExceptionHandler(BadHistoryRequestException.class)
    ResponseEntity<Map<String, String>> badRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid history account"));
    }

    @ExceptionHandler(HistoryAccountNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Account not found"));
    }

    @ExceptionHandler(HistoryDependencyException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "Account service unavailable"));
    }
}
