package com.nnson128.transfer.controller;

import com.nnson128.common.dto.TransferRequestDTO;
import com.nnson128.transfer.entity.Transaction;
import com.nnson128.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Validated
public class TransferController {
    private final TransferService transferService;

    @PostMapping
    public Transaction initiateTransfer(@NotNull @Valid @RequestBody TransferRequestDTO request) {
        return transferService.initiateTransfer(request);
    }

    @GetMapping("/{transactionId}/status")
    public Transaction getTransactionStatus(@PathVariable String transactionId) {
        return transferService.getTransactionStatus(transactionId);
    }
}
