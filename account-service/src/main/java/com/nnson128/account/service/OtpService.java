package com.nnson128.account.service;

import com.nnson128.account.kafka.AccountKafkaProducer;
import com.nnson128.common.event.OtpGeneratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final AccountKafkaProducer kafkaProducer;

    public String generateOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        redisTemplate.opsForValue().set("otp:" + email, otp, Duration.ofMinutes(5));
        
        // Publish Event
        String txId = java.util.UUID.randomUUID().toString();
        OtpGeneratedEvent event = OtpGeneratedEvent.builder()
                .transactionId(txId)
                .email(email)
                .otp(otp)
                .build();
                
        kafkaProducer.sendEvent("notification.otp", event);
        
        log.info("OTP generated for {}, event published.", email);
        return otp;
    }

    public boolean validateOtp(String email, String otp) {
        String storedOtp = redisTemplate.opsForValue().get("otp:" + email);
        return storedOtp != null && storedOtp.equals(otp);
    }
}
