package com.nnson128.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final org.thymeleaf.spring6.SpringTemplateEngine templateEngine;
    
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String fromEmail;

    public void sendEmail(String to, String subject, String body) {
        // Fallback for simple text emails if needed
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
        }
    }

    public void sendOtpEmail(String to, String otp) {
        try {
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("otp", otp);
            
            String htmlContent = templateEngine.process("otp-email", context);

            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ABC Bank - Verification Code");
            helper.setText(htmlContent, true); // true = isHtml
            
            mailSender.send(message);
            log.info("HTML OTP Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML OTP email to {}", to, e);
            throw new RuntimeException(e); // Rethrow to catch in listener for detailed logging if preferred
        }
    }

    public void sendBalanceChangeEmail(String to, String accountName, java.math.BigDecimal newBalance, 
                                       String accountNumber, String transactionType, 
                                       java.math.BigDecimal transactionAmount, String otherPartyName, 
                                       String otherPartyAccount, String description, String transactionId) {
        try {
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("accountName", accountName);
            context.setVariable("accountNumber", accountNumber);
            context.setVariable("formattedBalance", String.format("%,.0f ₫", newBalance));
            context.setVariable("transactionTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            context.setVariable("transactionType", transactionType);
            context.setVariable("transactionId", transactionId != null ? transactionId : "N/A");
            
            // Format transaction amount with + or - sign
            String sign = "CREDIT".equals(transactionType) ? "+" : "-";
            context.setVariable("formattedTransactionAmount", 
                sign + String.format("%,.0f ₫", transactionAmount));
            
            // Format other party info
            String otherPartyInfo = otherPartyName + " - " + otherPartyAccount;
            context.setVariable("otherPartyInfo", otherPartyInfo);
            context.setVariable("description", description != null ? description : "");
            
            String htmlContent = templateEngine.process("balance-change-email", context);

            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = 
                new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            
            // Dynamic subject based on transaction type
            String subject = "CREDIT".equals(transactionType) 
                ? "ABC Bank - Thông Báo Nhận Tiền" 
                : "ABC Bank - Thông Báo Chuyển Tiền";
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Balance change email sent to: {} (Type: {})", to, transactionType);
        } catch (Exception e) {
            log.error("Failed to send balance change email to {}", to, e);
        }
    }
}
