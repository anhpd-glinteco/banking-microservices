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
import com.nnson128.common.security.SecurityIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private static final java.util.regex.Pattern REQUEST_ID = java.util.regex.Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final HistoryRepository repository;
    private final RestTemplate accountRestTemplate;
    private final ObjectMapper objectMapper;
    private final EventMapper eventMapper;

    public List<HistoryLog> forAccountNumber(String accountNumber) {
        return forAccountNumber(accountNumber, null);
    }

    public List<HistoryLog> forAccountNumber(String accountNumber, String requestId) {
        if (accountNumber == null || accountNumber.isBlank()) throw new BadHistoryRequestException();
        authorizedAccount("/api/accounts/number/{account}", accountNumber, requestId);
        return repository.findOwnedByAccountNumber(accountNumber);
    }

    public List<HistoryLog> forLegacyAccountId(Long accountId) {
        return forLegacyAccountId(accountId, null);
    }

    public List<HistoryLog> forLegacyAccountId(Long accountId, String requestId) {
        if (accountId == null || accountId <= 0) throw new BadHistoryRequestException();
        JsonNode account = authorizedAccount("/api/accounts/{account}", accountId, requestId);
        return repository.findOwnedByAccountNumber(account.get("accountNumber").asText());
    }

    private JsonNode authorizedAccount(String path, Object account, String requestId) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authentication.getToken().getTokenValue());
        if (requestId != null && REQUEST_ID.matcher(requestId).matches()) {
            headers.set("X-TH7-Request-Id", requestId);
        }
        try {
            ResponseEntity<String> response = accountRestTemplate.exchange("http://account-service" + path,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class, account);
            if (response.getStatusCode().value() != 200) throw new HistoryDependencyException();
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode number = body == null ? null : body.get("accountNumber");
            JsonNode owner = body == null ? null : body.get("userId");
            if (number == null || !number.isTextual() || number.asText().isBlank()
                    || owner == null || !owner.isIntegralNumber() || !owner.canConvertToLong()) throw new HistoryDependencyException();
            if (account instanceof String && !account.equals(number.asText())) throw new HistoryDependencyException();
            SecurityIdentity.requireOwnerOrAdmin(owner.longValue());
            return body;
        } catch (HttpClientErrorException clientError) {
            if (clientError.getStatusCode() == HttpStatus.NOT_FOUND) throw new HistoryAccountNotFoundException();
            if (clientError.getStatusCode() == HttpStatus.UNAUTHORIZED)
                throw new AuthenticationCredentialsNotFoundException("Authentication required");
            if (clientError.getStatusCode() == HttpStatus.FORBIDDEN) throw new AccessDeniedException("Access denied");
            throw new HistoryDependencyException();
        } catch (AccessDeniedException | AuthenticationCredentialsNotFoundException | HistoryDependencyException known) {
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
