package com.nnson128.transfer.kafka;

import com.nnson128.common.event.BaseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferKafkaProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendEvent(String topic, BaseEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Sending event {}: {}", topic, payload);
            kafkaTemplate.send(topic, event.getTransactionId(), payload);
        } catch (Exception e) {
            log.error("Failed to serialize event: {}", event, e);
        }
    }
}
