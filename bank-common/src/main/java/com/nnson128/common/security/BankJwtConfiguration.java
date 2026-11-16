package com.nnson128.common.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;

@org.springframework.context.annotation.Import(SecurityExceptionHandler.class)
@EnableMethodSecurity
public class BankJwtConfiguration {
    public static final String ISSUER = "bank-kt1";
    public static final String AUDIENCE = "bank-api";

    @Bean
    public RSAPublicKey jwtPublicKey(@Value("${SECURITY_PUBLIC_KEY}") String path) throws Exception {
        String pem = Files.readString(Path.of(path)).replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid;
            try {
                String subject = jwt.getSubject();
                Object roles = jwt.getClaims().get("roles");
                valid = jwt.getAudience().contains(AUDIENCE)
                        && subject != null && subject.matches("[1-9][0-9]*") && Long.parseLong(subject) > 0
                        && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                        && roles instanceof java.util.List<?> list && !list.isEmpty()
                        && list.stream().allMatch(role -> "USER".equals(role) || "ADMIN".equals(role));
            } catch (IllegalArgumentException ex) {
                valid = false;
            }
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator(ISSUER), claims));
        return decoder;
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(java.util.List.of("http://127.0.0.1:5174", "http://localhost:5174", "http://127.0.0.1:5173", "http://localhost:5173"));
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
        cors.setAllowCredentials(false);
        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
