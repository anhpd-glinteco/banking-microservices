package com.nnson128.transfer.controller;

import com.nnson128.common.dto.TransferRequestDTO;
import com.nnson128.transfer.entity.Transaction;
import com.nnson128.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final TransferService transferService;

    @PostMapping
    public Transaction initiateTransfer(@RequestBody TransferRequestDTO request) {
        return transferService.initiateTransfer(request);
    }

    @GetMapping("/{transactionId}/status")
    public Transaction getTransactionStatus(@PathVariable String transactionId) {
        return transferService.getTransactionStatus(transactionId);
    }
}
