package com.nnson128.account.security;

import static org.junit.jupiter.api.Assertions.*;
import com.nnson128.account.entity.User;
import com.nnson128.common.security.BankJwtConfiguration;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.*;

class TokenSecurityTest {
    @TempDir Path temp;
    @Test void signedTokenHasValidatedClaimsAndRejectsTamperingAndWrongKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        Path privateFile = temp.resolve("private.pem");
        Files.writeString(privateFile, "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        Path publicFile = temp.resolve("public.pem");
        Files.writeString(publicFile, "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        var config = new BankJwtConfiguration();
        var publicKey = config.jwtPublicKey(publicFile.toString());
        var service = new TokenService(publicKey, privateFile.toString());
        String token = service.issue(User.builder().id(42L).role(User.Role.USER).build());
        Jwt decoded = config.jwtDecoder(publicKey).decode(token);
        assertEquals("42", decoded.getSubject());
        assertEquals("bank-kt1", decoded.getClaimAsString("iss"));
        assertEquals(java.util.List.of("bank-api"), decoded.getAudience());
        assertEquals(java.util.List.of("USER"), decoded.getClaimAsStringList("roles"));
        assertEquals(900, java.time.Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).toSeconds());
        assertEquals("ROLE_USER", config.jwtAuthenticationConverter().convert(decoded).getAuthorities().iterator().next().getAuthority());
        String[] parts = token.split("\\.");
        String altered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"999\"}".getBytes()) + "." + parts[2];
        assertThrows(JwtException.class, () -> config.jwtDecoder(publicKey).decode(altered));
        assertThrows(JwtException.class, () -> config.jwtDecoder((RSAPublicKey) generator.generateKeyPair().getPublic()).decode(token));
        var decoder = config.jwtDecoder(publicKey);
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "wrong-issuer", "bank-api", "42", java.util.List.of("USER"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "bank-kt1", "wrong-audience", "42", java.util.List.of("USER"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "bank-kt1", "bank-api", "999999999999999999999999", java.util.List.of("USER"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "bank-kt1", "bank-api", "42", 7, Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "bank-kt1", "bank-api", "42", java.util.List.of("ROOT"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(signed(keys, "bank-kt1", "bank-api", "42", java.util.List.of("USER"), Instant.now().minusSeconds(1))));
        assertThrows(Exception.class, () -> new TokenService(publicKey, temp.resolve("missing.pem").toString()));
        assertThrows(Exception.class, () -> new TokenService((RSAPublicKey) generator.generateKeyPair().getPublic(), privateFile.toString()));
    }
    private String signed(KeyPair keys, String issuer, String audience, String subject, Object roles, Instant expires) {
        var key = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keys.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(java.util.List.of(audience)).subject(subject)
                .issuedAt(Instant.now().minusSeconds(900)).expiresAt(expires).claim("roles", roles).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
