package com.nnson128.history.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnson128.history.entity.HistoryLog;
import com.nnson128.history.repository.HistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.*;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private final HistoryRepository repository;
    private final RestTemplate accountRestTemplate;
    private final ObjectMapper objectMapper;
    private final EventMapper eventMapper;

    @Value("${account.service.url:http://localhost:8081}")
    private String accountServiceUrl;

    public List<HistoryLog> forAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) throw new BadHistoryRequestException();
        return repository.findOwnedByAccountNumber(accountNumber);
    }

    public List<HistoryLog> forLegacyAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) throw new BadHistoryRequestException();
        try {
            ResponseEntity<String> response = accountRestTemplate.getForEntity(
                    accountServiceUrl + "/api/accounts/{id}", String.class, accountId);
            if (!response.getStatusCode().is2xxSuccessful()) throw new HistoryDependencyException();
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode number = body == null ? null : body.get("accountNumber");
            if (number == null || !number.isTextual() || number.asText().isBlank()) throw new HistoryDependencyException();
            return forAccountNumber(number.asText());
        } catch (HttpClientErrorException clientError) {
            if (clientError.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND)
                throw new HistoryAccountNotFoundException();
            throw new HistoryDependencyException();
        } catch (HistoryAccountNotFoundException | BadHistoryRequestException | HistoryDependencyException known) {
            throw known;
        } catch (Exception unavailable) {
            throw new HistoryDependencyException();
        }
    }

    @Transactional
    public HistoryLog archive(String raw) {
        HistoryLog mapped = eventMapper.map(raw);
        if (mapped == null) {
            mapped = HistoryLog.builder().eventType("KafkaEvent").eventData(raw).timestamp(java.time.LocalDateTime.now()).build();
        }
        if (mapped.getEventId() != null && repository.existsByEventId(mapped.getEventId())) return null;
        if (isFailed(mapped)) {
            HistoryLog owner = repository.findByTransactionId(mapped.getTransactionId()).stream()
                    .filter(row -> row.getFromAccountNumber() != null || row.getToAccountNumber() != null)
                    .findFirst().orElse(null);
            if (owner != null) copyOwners(owner, mapped);
        }
        HistoryLog saved = repository.save(mapped);
        if (saved.getTransactionId() != null && (saved.getFromAccountNumber() != null || saved.getToAccountNumber() != null)) {
            repository.findByTransactionId(saved.getTransactionId()).stream()
                    .filter(HistoryService::isFailed)
                    .filter(row -> row.getFromAccountNumber() == null && row.getToAccountNumber() == null)
                    .forEach(row -> { copyOwners(saved, row); repository.save(row); });
        }
        return saved;
    }

    @Transactional
    public BackfillReport backfillLegacyRows() {
        int updated = 0, duplicateIds = 0, malformed = 0;
        List<HistoryLog> rows = repository.findAll();
        Set<String> canonicalIds = new HashSet<>();
        java.util.Map<String, HistoryLog> ownersByTransaction = new java.util.HashMap<>();
        for (HistoryLog legacy : rows) {
            if (legacy.getEventData() == null) { malformed++; continue; }
            HistoryLog mapped = eventMapper.map(legacy.getEventData());
            if (mapped == null) { malformed++; continue; }
            if (mapped.getEventId() != null && !canonicalIds.add(mapped.getEventId())) {
                mapped.setEventId(null); duplicateIds++;
            }
            legacy.setEventId(mapped.getEventId());
            legacy.setTransactionId(mapped.getTransactionId() == null ? legacy.getTransactionId() : mapped.getTransactionId());
            legacy.setEventType(mapped.getEventType());
            legacy.setFromAccountNumber(mapped.getFromAccountNumber());
            legacy.setToAccountNumber(mapped.getToAccountNumber());
            if (legacy.getTransactionId() != null && (legacy.getFromAccountNumber() != null || legacy.getToAccountNumber() != null))
                ownersByTransaction.putIfAbsent(legacy.getTransactionId(), legacy);
            repository.save(legacy);
            updated++;
        }
        for (HistoryLog failure : rows) {
            if (!isFailed(failure) || failure.getTransactionId() == null || failure.getFromAccountNumber() != null || failure.getToAccountNumber() != null) continue;
            HistoryLog owner = ownersByTransaction.get(failure.getTransactionId());
            if (owner != null) { copyOwners(owner, failure); repository.save(failure); }
        }
        int unresolved = (int) rows.stream().filter(row -> row.getFromAccountNumber() == null && row.getToAccountNumber() == null).count();
        return new BackfillReport(updated, duplicateIds, unresolved, malformed);
    }

    private static boolean isFailed(HistoryLog row) { return "TRANSFER_FAILED".equals(row.getEventType()); }
    private static void copyOwners(HistoryLog source, HistoryLog target) {
        target.setFromAccountNumber(source.getFromAccountNumber());
        target.setToAccountNumber(source.getToAccountNumber());
    }
    public record BackfillReport(int updated, int duplicateIds, int unresolved, int malformed) { }

    public static class BadHistoryRequestException extends RuntimeException { }
    public static class HistoryAccountNotFoundException extends RuntimeException { }
    public static class HistoryDependencyException extends RuntimeException { }
}
