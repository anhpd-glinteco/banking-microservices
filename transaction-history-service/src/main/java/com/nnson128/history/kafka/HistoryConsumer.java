package com.nnson128.history.kafka;

import com.nnson128.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoryConsumer {

    private final HistoryService historyService;

    @KafkaListener(topics = "banking.transaction", groupId = "history-group")
    public void logEvent(String message) {
        historyService.archive(message);
    }
}
