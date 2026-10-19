package com.nnson128.transfer.client;

import com.nnson128.common.dto.AccountDTO;
import com.nnson128.common.exception.ResourceNotFoundException;
import com.nnson128.transfer.exception.AccountServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.HttpURLConnection;

@Component
public class AccountClient {
    private final RestClient client;

    @Autowired
    public AccountClient(@Value("${account.service.url:http://localhost:8081}") String baseUrl) {
        this(baseUrl, 2000, 3000);
    }

    AccountClient(String baseUrl, int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public void requireAccount(String accountNumber) {
        AccountDTO account;
        try {
            account = client.get().uri("/api/accounts/number/{accountNumber}", accountNumber)
                    .retrieve()
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
        }
        if (account == null || accountNumber == null || !accountNumber.equals(account.getAccountNumber())) {
            throw new AccountServiceUnavailableException();
        }
    }
}
