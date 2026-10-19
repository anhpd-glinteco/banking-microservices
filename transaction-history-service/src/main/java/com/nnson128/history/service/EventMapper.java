package com.nnson128.history.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnson128.history.entity.HistoryLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EventMapper {
    private final ObjectMapper objectMapper;

    public HistoryLog map(String raw) {
        try {
            JsonNode event = objectMapper.readTree(raw);
            if (!event.isObject()) return null;
            String from = text(event, "fromAccountId");
            String to = text(event, "toAccountId");
            String type = text(event, "eventType");
            if (type == null) {
                if ("SUCCESS".equalsIgnoreCase(text(event, "status"))) type = "TRANSFER_COMPLETED";
                else if (text(event, "reason") != null) type = from == null ? "TRANSFER_FAILED" : "COMPENSATION";
                else type = "KafkaEvent";
            }
            return HistoryLog.builder()
                    .eventId(text(event, "id"))
                    .transactionId(text(event, "transactionId"))
                    .eventType(type)
                    .fromAccountNumber(from)
                    .toAccountNumber(to)
                    .eventData(raw)
                    .timestamp(timestamp(event))
                    .build();
        } catch (Exception malformed) {
            return null;
        }
    }

    private LocalDateTime timestamp(JsonNode node) {
        String value = text(node, "timestamp");
        if (value == null) return LocalDateTime.now();
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException invalid) { return LocalDateTime.now(); }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
