package com.nnson128.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableDiscoveryClient
@org.springframework.context.annotation.Import(com.nnson128.common.exception.GlobalExceptionHandler.class)
public class TransferApplication {
	@Bean
	@LoadBalanced
	RestClient.Builder accountRestClientBuilder() {
		return RestClient.builder();
	}

	public static void main(String[] args) {
		SpringApplication.run(TransferApplication.class, args);
	}
}
