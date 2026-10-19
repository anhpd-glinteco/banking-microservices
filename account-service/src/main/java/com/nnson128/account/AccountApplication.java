package com.nnson128.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@org.springframework.context.annotation.Import(com.nnson128.common.exception.GlobalExceptionHandler.class)
public class AccountApplication {
	public static void main(String[] args) {
		SpringApplication.run(AccountApplication.class, args);
	}
}
