package com.nnson128.notification.kafka;

import com.nnson128.common.event.BalanceChangedEvent;
import com.nnson128.common.event.OtpGeneratedEvent;
import com.nnson128.common.event.TransferCompletedEvent;
import com.nnson128.common.event.TransferFailedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnson128.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "banking.account", groupId = "notification-group")
    public void handleAccountEvents(String message) {
        try {
            BalanceChangedEvent event = objectMapper.readValue(message, BalanceChangedEvent.class);
            log.info("Processing balance change notification.");
            
            // Send WebSocket notification to specific account
            messagingTemplate.convertAndSend("/topic/account/" + event.getAccountId(), event);
            
            // Send professional HTML email notification with full transaction details
            if (event.getEmail() != null && !event.getEmail().isEmpty()) {
                emailService.sendBalanceChangeEmail(
                    event.getEmail(), 
                    event.getAccountName(), 
                    event.getNewBalance(),
                    event.getAccountId(),
                    event.getTransactionType(),
                    event.getTransactionAmount(),
                    event.getOtherPartyName(),
                    event.getOtherPartyAccount(),
                    event.getDescription(),
                    event.getTransactionId()
                );
            }
        } catch (Exception e) {
            log.error("Balance change notification failed ({})", e.getClass().getSimpleName());
        }
    }

    @KafkaListener(topics = "banking.transaction", groupId = "notification-group")
    public void handleTransactionEvents(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("status") && "SUCCESS".equals(node.get("status").asText())) {
                TransferCompletedEvent e = objectMapper.treeToValue(node, TransferCompletedEvent.class);
                log.info("Transfer {} completed.", e.getTransactionId());
            } else if (node.has("reason") && !node.has("amount")) {
                 TransferFailedEvent e = objectMapper.treeToValue(node, TransferFailedEvent.class);
                 log.info("Transfer {} failed.", e.getTransactionId());
            }
        } catch (Exception e) {
             log.error("Transfer notification processing failed ({})", e.getClass().getSimpleName());
        }
    }
    
    @KafkaListener(topics = "notification.otp", groupId = "notification-group")
    public void handleOtpEvents(String message) {
        try {
            OtpGeneratedEvent event = objectMapper.readValue(message, OtpGeneratedEvent.class);
            emailService.sendOtpEmail(event.getEmail(), event.getOtp());
        } catch (Exception e) {
            log.error("OTP notification processing failed ({})", e.getClass().getSimpleName());
        }
    }
}
