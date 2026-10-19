package com.nnson128.history.controller;

import com.nnson128.history.entity.HistoryLog;
import com.nnson128.history.repository.HistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryRepository repository;

    @GetMapping("/{accountId}")
    public List<HistoryLog> getHistory(@PathVariable String accountId) {
        return repository.findAll(); 
    }
}
