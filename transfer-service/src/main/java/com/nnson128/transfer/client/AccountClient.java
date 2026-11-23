package com.nnson128.transfer.client;

import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.exception.ResourceNotFoundException;
import com.nnson128.transfer.exception.AccountServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.nnson128.common.security.SecurityIdentity;
import com.nnson128.transfer.entity.Transaction;

import java.io.IOException;
import java.net.HttpURLConnection;

@Component
public class AccountClient {
    private final RestClient client;

    @Autowired
    public AccountClient(@LoadBalanced RestClient.Builder builder,
            @Value("${account.service.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${account.service.read-timeout-ms:3000}") int readTimeout) {
        this(builder, "http://account-service", connectTimeout, readTimeout);
    }

    AccountClient(RestClient client) { this.client = client; }

    AccountClient(String baseUrl) { this(baseUrl, 2000, 3000); }

    AccountClient(String baseUrl, int connectTimeout, int readTimeout) {
        this(RestClient.builder(), baseUrl, connectTimeout, readTimeout);
    }

    private AccountClient(RestClient.Builder builder, String baseUrl, int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public void requireSender(String accountNumber) {
        AccountDTO account = fetch(accountNumber, false);
        SecurityIdentity.requireOwner(account.getUserId());
    }

    public void requireAccount(String accountNumber) {
        fetch(accountNumber, true);
    }

    public void requireParticipant(Transaction transaction) {
        if (SecurityIdentity.isAdmin()) return;
        try {
            requireSender(transaction.getFromAccountNumber());
            return;
        } catch (AccessDeniedException denied) {
            // A recipient may read a transfer even when the sender belongs to someone else.
        }
        requireSender(transaction.getToAccountNumber());
    }

    private AccountDTO fetch(String accountNumber, boolean lookup) {
        AccountDTO account;
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        try {
            account = client.get().uri(lookup ? "/api/accounts/lookup/{accountNumber}" : "/api/accounts/number/{accountNumber}", accountNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve()
                    .onStatus(status -> status.value() == 401,
                            (request, response) -> { throw new AuthenticationCredentialsNotFoundException("Authentication required"); })
                    .onStatus(status -> status.value() == 403,
                            (request, response) -> { throw new AccessDeniedException("Access denied"); })
                    .onStatus(status -> status.value() != 200 && status.value() != 404,
                            (request, response) -> { throw new AccountServiceUnavailableException(); })
                    .body(AccountDTO.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Account not found");
            }
            throw new AccountServiceUnavailableException();
        } catch (RestClientException ex) {
            throw new AccountServiceUnavailableException();
        } catch (IllegalStateException ex) {
            throw new AccountServiceUnavailableException();
        } catch (IllegalArgumentException ex) {
            throw new AccountServiceUnavailableException();
        }
        if (account == null || accountNumber == null || !accountNumber.equals(account.getAccountNumber())
                || (!lookup && account.getUserId() == null)) {
            throw new AccountServiceUnavailableException();
        }
        return account;
    }
}
