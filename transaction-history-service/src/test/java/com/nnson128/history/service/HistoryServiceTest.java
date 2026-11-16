package com.nnson128.history.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnson128.history.entity.HistoryLog;
import com.nnson128.history.repository.HistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {
    private HistoryRepository repository;
    private RestTemplate accountClient;
    private HistoryService service;
    private final List<HistoryLog> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("1").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        repository = mock(HistoryRepository.class);
        accountClient = mock(RestTemplate.class);
        EventMapper mapper = new EventMapper(new ObjectMapper());
        service = new HistoryService(repository, accountClient, new ObjectMapper(), mapper);
        ReflectionTestUtils.setField(service, "accountServiceUrl", "http://account");
        when(repository.findByTransactionId(anyString())).thenAnswer(inv -> rows.stream()
                .filter(row -> inv.getArgument(0).equals(row.getTransactionId())).toList());
        when(repository.save(any())).thenAnswer(inv -> {
            HistoryLog row = inv.getArgument(0);
            if (!rows.contains(row)) rows.add(row);
            return row;
        });
    }

    @Test
    void ownerQueryUsesExactAccountNumberAndPreservesLeadingZeroes() {
        when(accountClient.exchange(eq("http://account/api/accounts/number/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq("00123")))
                .thenReturn(ResponseEntity.ok("{\"userId\":1,\"accountNumber\":\"00123\"}"));
        when(repository.findOwnedByAccountNumber("00123")).thenReturn(List.of());
        assertTrue(service.forAccountNumber("00123").isEmpty());
        verify(repository).findOwnedByAccountNumber("00123");
    }

    @Test
    void legacyIdResolvesAccountNumberInsteadOfCastingId() {
        when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(123L)))
                .thenReturn(ResponseEntity.ok("{\"id\":123,\"userId\":1,\"accountNumber\":\"00123\"}"));
        when(repository.findOwnedByAccountNumber("00123")).thenReturn(List.of());
        service.forLegacyAccountId(123L);
        verify(repository).findOwnedByAccountNumber("00123");
    }

    @Test
    void unknownLegacyAccountRemains404AndUnavailableAccountServiceIs503() {
        when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(9L)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        assertThrows(HistoryService.HistoryAccountNotFoundException.class, () -> service.forLegacyAccountId(9L));
        when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(10L)))
                .thenThrow(new IllegalStateException("offline"));
        assertThrows(HistoryService.HistoryDependencyException.class, () -> service.forLegacyAccountId(10L));
        when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(11L)))
                .thenReturn(ResponseEntity.ok("{}"));
        assertThrows(HistoryService.HistoryDependencyException.class, () -> service.forLegacyAccountId(11L));
        assertThrows(HistoryService.BadHistoryRequestException.class, () -> service.forLegacyAccountId(0L));
    }

    @AfterEach
    void clearSecurity() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test
    void authorizationErrorsAndForeignOwnerNeverReadHistory() {
        when(accountClient.exchange(eq("http://account/api/accounts/number/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq("foreign")))
                .thenReturn(ResponseEntity.ok("{\"userId\":2,\"accountNumber\":\"foreign\"}"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.forAccountNumber("foreign"));
        for (HttpStatus code : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(9L)))
                    .thenThrow(new HttpClientErrorException(code));
            Class<? extends RuntimeException> expected = code == HttpStatus.UNAUTHORIZED
                    ? org.springframework.security.core.AuthenticationException.class
                    : org.springframework.security.access.AccessDeniedException.class;
            assertThrows(expected, () -> service.forLegacyAccountId(9L));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void adminMayReadAnotherOwnersHistoryAndBearerIsForwarded() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(accountClient.exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class), eq(9L)))
                .thenReturn(ResponseEntity.ok("{\"userId\":2,\"accountNumber\":\"foreign\"}"));
        service.forLegacyAccountId(9L);
        verify(accountClient).exchange(eq("http://account/api/accounts/{account}"), eq(HttpMethod.GET),
                argThat((HttpEntity<?> entity) -> "Bearer fixture".equals(entity.getHeaders().getFirst("Authorization"))), eq(String.class), eq(9L));
        verify(repository).findOwnedByAccountNumber("foreign");
    }

    @Test
    void mappingPreservesRawPayloadAndUsesStringAccountNumbers() {
        String raw = "{\"id\":\"evt-1\",\"transactionId\":\"tx-1\",\"fromAccountId\":\"00123\",\"toAccountId\":\"TH4-B\",\"amount\":10}";
        HistoryLog mapped = new EventMapper(new ObjectMapper()).map(raw);
        assertEquals("evt-1", mapped.getEventId());
        assertEquals("00123", mapped.getFromAccountNumber());
        assertEquals("TH4-B", mapped.getToAccountNumber());
        assertEquals(raw, mapped.getEventData());
        assertEquals("KafkaEvent", mapped.getEventType());
    }

    @Test
    void fallbackTypesUsePayloadSignalsWithoutChangingRawData() {
        EventMapper mapper = new EventMapper(new ObjectMapper());
        String success = "{\"status\":\"SUCCESS\"}";
        String failure = "{\"transactionId\":\"tx\",\"reason\":\"declined\"}";
        String compensation = "{\"transactionId\":\"tx\",\"reason\":\"refund\",\"fromAccountId\":\"TH4-A\"}";
        assertEquals("TRANSFER_COMPLETED", mapper.map(success).getEventType());
        assertEquals("TRANSFER_FAILED", mapper.map(failure).getEventType());
        assertEquals("COMPENSATION", mapper.map(compensation).getEventType());
        assertEquals(compensation, mapper.map(compensation).getEventData());
    }

    @Test
    void failedEventBeforeOwnerIsResolvedWhenCounterpartArrivesAndDuplicatesAreIgnored() {
        String failed = "{\"id\":\"f-1\",\"transactionId\":\"tx-2\",\"reason\":\"declined\"}";
        HistoryLog failedRow = service.archive(failed);
        assertNull(failedRow.getFromAccountNumber());

        service.archive("{\"id\":\"c-1\",\"transactionId\":\"tx-2\",\"eventType\":\"MONEY_DEDUCTED\",\"fromAccountId\":\"TH4-A\",\"toAccountId\":\"TH4-B\"}");
        assertEquals("TH4-A", failedRow.getFromAccountNumber());
        assertEquals("TH4-B", failedRow.getToAccountNumber());

        when(repository.existsByEventId("f-1")).thenReturn(true);
        assertNull(service.archive(failed));
        assertEquals(2, rows.size());
    }

    @Test
    void databaseSaveFailureIsPropagatedForKafkaRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(repository).save(any(HistoryLog.class));
        assertThrows(IllegalStateException.class, () -> service.archive("{\"transactionId\":\"tx\"}"));
    }

    @Test
    void explicitBackfillKeepsMalformedRawRecordAndNullsDuplicateIds() {
        HistoryLog first = HistoryLog.builder().id(1L).eventData("{\"id\":\"same\",\"transactionId\":\"tx-a\",\"fromAccountId\":\"TH4-A\"}").build();
        HistoryLog duplicate = HistoryLog.builder().id(2L).eventData("{\"id\":\"same\",\"transactionId\":\"tx-b\",\"toAccountId\":\"TH4-B\"}").build();
        HistoryLog malformed = HistoryLog.builder().id(3L).eventData("{bad json").build();
        when(repository.findAll()).thenReturn(List.of(first, duplicate, malformed));
        HistoryService.BackfillReport report = service.backfillLegacyRows();
        assertEquals("same", first.getEventId());
        assertNull(duplicate.getEventId());
        assertEquals("{bad json", malformed.getEventData());
        assertNull(malformed.getFromAccountNumber());
        assertEquals(1, report.duplicateIds());
        assertEquals(1, report.malformed());
        assertEquals(1, report.unresolved());
    }
}
