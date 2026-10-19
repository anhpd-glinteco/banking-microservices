package com.nnson128.transfer.kafka;

import com.nnson128.common.event.TransferCompletedEvent;
import com.nnson128.common.event.TransferFailedEvent;
import com.nnson128.transfer.service.TransferService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferKafkaConsumer {
    private final TransferService transferService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.transaction", groupId = "transfer-group")
    public void handleTransactionEvents(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // TransferCompletedEvent: status=SUCCESS, from, to, amount
            if (node.has("status") && "SUCCESS".equals(node.get("status").asText())) {
                 TransferCompletedEvent event = objectMapper.treeToValue(node, TransferCompletedEvent.class);
                 log.info("Event: TransferCompletedEvent");
                 transferService.handleTransferCompleted(event);
            } else if (node.has("reason") && !node.has("amount")) { 
                // TransferFailedEvent matches reason. 
                // Compensation has 'reason' too but also 'amount'. 
                // TransferFailed might be pure reason? The class definition: reason only.
                TransferFailedEvent event = objectMapper.treeToValue(node, TransferFailedEvent.class);
                log.info("Event: TransferFailedEvent");
                transferService.handleTransferFailed(event);
            }
        } catch (Exception e) {
            log.error("Error in TransferConsumer: {}", message, e);
        }
    }
}
