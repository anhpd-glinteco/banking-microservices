package com.nnson128.history.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class HistoryClientConfig {
    @Bean
    RestTemplate accountRestTemplate(RestTemplateBuilder builder,
            @Value("${account.service.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${account.service.read-timeout-ms:3000}") int readTimeout) {
        return builder.setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout)).build();
    }
}
