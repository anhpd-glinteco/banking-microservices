package com.nnson128.transfer.client;

import com.nnson128.common.exception.GlobalExceptionHandler;
import com.nnson128.common.dto.TransferRequestDTO;
import com.nnson128.transfer.controller.TransferController;
import com.nnson128.transfer.controller.TransferExceptionHandler;
import com.nnson128.transfer.entity.Transaction;
import com.nnson128.transfer.kafka.TransferKafkaProducer;
import com.nnson128.transfer.repository.TransactionRepository;
import com.nnson128.transfer.service.TransferService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountPreflightTest {
    private static final String REQUEST = """
            {"fromAccountNumber":"001","toAccountNumber":"002","amount":1000,"description":"TH4 fixture"}
            """;
    private HttpServer server;
    private ExecutorService executor;
    private TransactionRepository repository;
    private TransferKafkaProducer producer;
    private MockMvc mvc;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private String failureAccount;
    private int upstreamStatus = 200;
    private String upstreamBody;
    private boolean slow;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(TransactionRepository.class);
        producer = mock(TransferKafkaProducer.class);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("1").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        server.createContext("/api/accounts/", exchange -> {
            assertEquals("Bearer fixture", exchange.getRequestHeaders().getFirst("Authorization"));
            String rawPath = exchange.getRequestURI().getRawPath();
            paths.add(rawPath);
            String number = exchange.getRequestURI().getPath().substring("/api/accounts/number/".length());
            boolean fail = number.equals(failureAccount);
            if (fail && slow) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            String body = fail && upstreamBody != null ? upstreamBody
                    : "{\"id\":1,\"userId\":1,\"accountNumber\":\"" + number
                    + "\",\"accountName\":\"Fixture\",\"balance\":10000}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (fail && upstreamStatus == 302) {
                exchange.getResponseHeaders().set("Location", "/api/accounts/number/002");
            }
            try (exchange) {
                exchange.sendResponseHeaders(fail ? upstreamStatus : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        AccountClient client = new AccountClient("http://127.0.0.1:" + server.getAddress().getPort(), 250, 250);
        mvc = MockMvcBuilders.standaloneSetup(new TransferController(new TransferService(repository, producer, client)))
                .setControllerAdvice(new GlobalExceptionHandler(), new TransferExceptionHandler(),
                        new com.nnson128.common.security.SecurityExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void validAccountsAreCheckedBeforePendingSaveAndEvent() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            assertEquals(List.of("/api/accounts/number/001", "/api/accounts/lookup/002"), paths);
            tx.ensureId();
            return tx;
        });
        doAnswer(invocation -> {
            verify(repository).save(argThat(tx -> "PENDING".equals(tx.getStatus())));
            return null;
        }).when(producer).sendEvent(eq("banking.transaction"), any());

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.fromAccountNumber").value("001"))
                .andExpect(jsonPath("$.toAccountNumber").value("002"))
                .andExpect(jsonPath("$.description").value("TH4 fixture"));
        verify(repository, times(2)).save(any());
        verify(producer).sendEvent(eq("banking.transaction"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PROCESSING"})
    void duplicateStillChecksSenderBeforeReturningWithoutWrites(String status) throws Exception {
        Transaction existing = Transaction.builder().id("existing").status(status).build();
        when(repository.findPendingOrProcessingTransaction(anyString(), anyString(), any())).thenReturn(existing);
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("existing"))
                .andExpect(jsonPath("$.status").value(status));
        assertEquals(List.of("/api/accounts/number/001"), paths);
        assertNoWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"001", "002"})
    void missingSourceOrDestinationReturnsSafe404(String number) throws Exception {
        failureAccount = number;
        upstreamStatus = 404;
        upstreamBody = "secret upstream body";
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Requested resource was not found"));
        assertEquals(number.equals("001") ? 1 : 2, paths.size());
        assertNoWrites();
    }

    @ParameterizedTest
    @CsvSource({"001,201", "002,204", "001,302", "001,400", "002,409", "001,500", "002,503"})
    void upstreamFailuresAreSafe503(String number, int status) throws Exception {
        failureAccount = number;
        upstreamStatus = status;
        upstreamBody = "secret upstream body";
        assertUnavailable();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"accountNumber\":\"wrong\"}", "not-json", "[]", "{\"accountNumber\":{}}", ""})
    void invalidUpstreamBodyIsSafe503(String body) throws Exception {
        failureAccount = "002";
        upstreamBody = body;
        assertUnavailable();
    }

    @ParameterizedTest
    @ValueSource(strings = {"001", "002"})
    void readTimeoutIsSafe503(String number) throws Exception {
        failureAccount = number;
        slow = true;
        assertUnavailable();
    }

    @Test
    void refusedConnectionIsSafe503() throws Exception {
        server.stop(0);
        assertUnavailable();
    }

    @Test
    void missingDiscoveredServiceInstanceIsSafe503() {
        org.springframework.http.client.ClientHttpRequestFactory requestFactory = (uri, method) -> {
            throw new IllegalArgumentException("No servers available for service: account-service");
        };
        AccountClient client = new AccountClient(org.springframework.web.client.RestClient.builder()
                .requestFactory(requestFactory).build());

        assertThrows(com.nnson128.transfer.exception.AccountServiceUnavailableException.class,
                () -> client.requireAccount("002"));
        assertNoWrites();
    }

    @Test
    void clientEncodesAccountNumberAsSinglePathVariable() {
        AccountClient client = new AccountClient("http://127.0.0.1:" + server.getAddress().getPort());
        client.requireAccount("00/1 +?#");
        assertEquals(List.of("/api/accounts/lookup/00%2F1%20%2B%3F%23"), paths);
    }

    @Test
    void existingStatusEndpointIsUnchanged() throws Exception {
        when(repository.findById("existing")).thenReturn(Optional.of(
                Transaction.builder().id("existing").fromAccountNumber("001").toAccountNumber("002").status("SUCCESS").build()));
        mvc.perform(get("/api/transfers/existing/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(List.of("/api/accounts/number/001"), paths);
        assertNoWrites();
    }

    @Test
    void invalidTransferIsRejectedBeforeAccountLookupPersistenceOrEvent() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountNumber\":\"001\",\"toAccountNumber\":\"001\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.rejectedValue").doesNotExist());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
        assertTrue(paths.isEmpty());
        assertNoWrites();

        TransferRequestDTO invalid = new TransferRequestDTO();
        invalid.setFromAccountNumber("001");
        invalid.setToAccountNumber("002");
        invalid.setAmount(new java.math.BigDecimal("0"));
        assertThrows(IllegalArgumentException.class, () -> new TransferService(repository, producer,
                new AccountClient("http://127.0.0.1:" + server.getAddress().getPort())).initiateTransfer(invalid));
        assertTrue(paths.isEmpty());
        assertNoWrites();
    }

    @ParameterizedTest
    @CsvSource({"001,401", "001,403", "002,401", "002,403"})
    void authorizationFailuresPropagateWithoutWrites(String number, int code) throws Exception {
        failureAccount = number;
        upstreamStatus = code;
        upstreamBody = "secret";
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().is(code));
        assertNoWrites();
    }

    @Test
    void adminCannotSpendAnotherOwnersFundsEvenForDuplicate() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("2").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, producer);
    }

    @Test
    void recipientCanReadStatusButUnrelatedUserCannot() throws Exception {
        when(repository.findById("existing")).thenReturn(Optional.of(Transaction.builder().id("existing")
                .fromAccountNumber("001").toAccountNumber("002").status("SUCCESS").build()));
        failureAccount = "001";
        upstreamStatus = 403;
        mvc.perform(get("/api/transfers/existing/status")).andExpect(status().isOk());
        failureAccount = "002";
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("3").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        mvc.perform(get("/api/transfers/existing/status")).andExpect(status().isForbidden());
        assertNoWrites();
    }

    @Test
    void adminMayReadStatusWithoutOwningEitherAccount() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9").build(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(repository.findById("existing")).thenReturn(Optional.of(Transaction.builder().id("existing")
                .fromAccountNumber("001").toAccountNumber("002").status("SUCCESS").build()));
        mvc.perform(get("/api/transfers/existing/status")).andExpect(status().isOk());
        assertTrue(paths.isEmpty());
        assertNoWrites();
    }

    @Test
    void missingTransferReturnsSafe404() throws Exception {
        mvc.perform(get("/api/transfers/missing/status")).andExpect(status().isNotFound());
        assertTrue(paths.isEmpty());
        assertNoWrites();
    }

    private void assertUnavailable() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Account service unavailable"))
                .andExpect(jsonPath("$.path").value("/api/transfers"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertNoWrites();
    }

    private void assertNoWrites() {
        verify(repository, never()).save(any());
        verifyNoInteractions(producer);
    }
}
