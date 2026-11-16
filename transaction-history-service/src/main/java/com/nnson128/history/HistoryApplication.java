package com.nnson128.history;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@org.springframework.context.annotation.Import(com.nnson128.common.exception.GlobalExceptionHandler.class)
public class HistoryApplication {
	public static void main(String[] args) {
		SpringApplication.run(HistoryApplication.class, args);
	}
}
