package com.nnson128.account.security;

import com.nnson128.common.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import(BankJwtConfiguration.class)
public class AccountSecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain accountSecurity(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/accounts/login", "/api/accounts/otp/generate",
                        "/api/accounts/users/register-with-otp", "/api/accounts/forgot-password", "/api/accounts/reset-password").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/accounts/check-exists").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/users", "/api/loans/*/approve").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/accounts/users").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/accounts/users/*").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(SecurityErrors::write401).accessDeniedHandler(SecurityErrors::write403))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(SecurityErrors::write401).accessDeniedHandler(SecurityErrors::write403))
                .build();
    }
}
