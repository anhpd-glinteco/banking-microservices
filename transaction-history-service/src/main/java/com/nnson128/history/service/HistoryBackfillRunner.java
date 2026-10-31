package com.nnson128.history.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "history.backfill.enabled", havingValue = "true")
public class HistoryBackfillRunner implements ApplicationRunner {
    private final HistoryService historyService;

    @Override
    public void run(ApplicationArguments args) {
        HistoryService.BackfillReport report = historyService.backfillLegacyRows();
        log.info("History legacy backfill: updated={}, duplicateIds={}, unresolved={}, malformed={}",
                report.updated(), report.duplicateIds(), report.unresolved(), report.malformed());
    }
}
