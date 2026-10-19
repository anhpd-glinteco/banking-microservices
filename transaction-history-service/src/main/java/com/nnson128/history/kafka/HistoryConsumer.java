package com.nnson128.history.kafka;

import com.nnson128.history.entity.HistoryLog;
import com.nnson128.history.repository.HistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoryConsumer {

    private final HistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.transaction", groupId = "history-group")
    public void logEvent(String message) {
        log.info("Archiving Event Payload: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String transactionId = node.has("transactionId") ? node.get("transactionId").asText() : "UNKNOWN";
            
             // Create log
            HistoryLog logEntry = HistoryLog.builder()
                    .transactionId(transactionId)
                    .eventType("KafkaEvent") // We could infer exact type from fields
                    .eventData(message) // Store Raw JSON
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
            historyRepository.save(logEntry);
            
        } catch (Exception e) {
            log.error("Failed to archive: {}", message);
        }
    }
}
