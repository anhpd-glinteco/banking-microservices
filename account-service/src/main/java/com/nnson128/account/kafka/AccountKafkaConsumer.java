package com.nnson128.account.kafka;

import com.nnson128.account.service.AccountService;
import com.nnson128.common.event.CompensationEvent;
import com.nnson128.common.event.MoneyDeductedEvent;
import com.nnson128.common.event.TransactionCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountKafkaConsumer {

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.transaction", groupId = "account-deductor")
    public void onTransactionCreated(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("eventType") && "TRANSACTION_CREATED".equals(node.get("eventType").asText())) {
                 TransactionCreatedEvent event = objectMapper.treeToValue(node, TransactionCreatedEvent.class);
                 log.info("Processing TransactionCreatedEvent: {}", event.getId());
                 accountService.handleTransactionCreated(event);
            }
        } catch (Exception e) {
            log.error("Error processing message in deductor: {}", message, e);
        }
    }

    @KafkaListener(topics = "banking.transaction", groupId = "account-creditor")
    public void onMoneyDeducted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("eventType") && "MONEY_DEDUCTED".equals(node.get("eventType").asText())) {
                 MoneyDeductedEvent event = objectMapper.treeToValue(node, MoneyDeductedEvent.class);
                 log.info("Processing MoneyDeductedEvent: {}", event.getId());
                 accountService.handleMoneyDeducted(event);
            }
        } catch (Exception e) {
             log.error("Error processing message in creditor: {}", message, e);
        }
    }

    @KafkaListener(topics = "banking.transaction", groupId = "account-compensator")
    public void onCompensation(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("eventType") && "COMPENSATION".equals(node.get("eventType").asText())) {
                CompensationEvent event = objectMapper.treeToValue(node, CompensationEvent.class);
                log.info("Processing CompensationEvent: {}", event.getId());
                accountService.handleCompensation(event);
            }
        } catch (Exception e) {
             log.error("Error processing message in compensator: {}", message, e);
        }
    }
}
