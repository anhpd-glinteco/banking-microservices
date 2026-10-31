package com.nnson128.transfer.service;

import com.nnson128.common.dto.TransferRequestDTO;
import com.nnson128.common.event.TransactionCreatedEvent;
import com.nnson128.common.event.TransferCompletedEvent;
import com.nnson128.common.event.TransferFailedEvent;
import com.nnson128.transfer.entity.Transaction;
import com.nnson128.transfer.client.AccountClient;
import com.nnson128.transfer.kafka.TransferKafkaProducer;
import com.nnson128.transfer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    private final TransactionRepository transactionRepository;
    private final TransferKafkaProducer kafkaProducer;
    private final AccountClient accountClient;

    public Transaction initiateTransfer(TransferRequestDTO request) {
        if (request == null || request.getFromAccountNumber() == null || request.getFromAccountNumber().isBlank()
                || request.getFromAccountNumber().length() > 255
                || request.getToAccountNumber() == null || request.getToAccountNumber().isBlank()
                || request.getToAccountNumber().length() > 255
                || request.getFromAccountNumber().equals(request.getToAccountNumber())
                || request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().scale() > 2 || request.getAmount().precision() - request.getAmount().scale() > 13
                || (request.getDescription() != null && request.getDescription().length() > 255)) {
            throw new IllegalArgumentException("Transfer fields are invalid");
        }
        // Check for duplicate request (idempotency)
        Transaction existingTx = transactionRepository.findPendingOrProcessingTransaction(
            request.getFromAccountNumber(), 
            request.getToAccountNumber(), 
            request.getAmount()
        );
        
        if (existingTx != null) {
            log.warn("Duplicate transfer request detected, returning existing transaction: {}", existingTx.getId());
            return existingTx;
        }

        accountClient.requireAccount(request.getFromAccountNumber());
        accountClient.requireAccount(request.getToAccountNumber());

        Transaction tx = Transaction.builder()
                .fromAccountNumber(request.getFromAccountNumber())
                .toAccountNumber(request.getToAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status("PENDING")
                .retryCount(0)
                .build();
        transactionRepository.save(tx);

        // Publish Event to start Saga
        TransactionCreatedEvent event = TransactionCreatedEvent.builder()
                .transactionId(tx.getId())
                .fromAccountId(request.getFromAccountNumber())
                .toAccountId(request.getToAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .eventType("TRANSACTION_CREATED")
                .build();
        
        kafkaProducer.sendEvent("banking.transaction", event);
        
        // Update status to PROCESSING
        tx.setStatus("PROCESSING");
        transactionRepository.save(tx);
        
        return tx;
    }

    @Transactional
    public void handleTransferCompleted(TransferCompletedEvent event) {
        transactionRepository.findById(event.getTransactionId()).ifPresent(tx -> {
            tx.setStatus("SUCCESS");
            transactionRepository.save(tx);
            log.info("Transaction {} completed successfully.", tx.getId());
        });
    }

    @Transactional
    public void handleTransferFailed(TransferFailedEvent event) {
        transactionRepository.findById(event.getTransactionId()).ifPresent(tx -> {
            tx.setStatus("FAILED");
            tx.setFailureReason(event.getReason());
            tx.setRetryCount(tx.getRetryCount() + 1);
            transactionRepository.save(tx);
            log.warn("Transaction {} failed: {} (Retry count: {})", tx.getId(), event.getReason(), tx.getRetryCount());
        });
    }

    public Transaction getTransactionStatus(String transactionId) {
        return transactionRepository.findById(transactionId).orElse(null);
    }
}
