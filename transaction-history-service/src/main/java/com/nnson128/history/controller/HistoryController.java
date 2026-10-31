package com.nnson128.history.controller;

import com.nnson128.history.entity.HistoryLog;
import com.nnson128.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryService historyService;

    @GetMapping("/number/{accountNumber}")
    public List<HistoryLog> byAccountNumber(@PathVariable String accountNumber) {
        return historyService.forAccountNumber(accountNumber);
    }

    @GetMapping("/{accountId:\\d+}")
    public List<HistoryLog> byLegacyAccountId(@PathVariable Long accountId) {
        return historyService.forLegacyAccountId(accountId);
    }
}
