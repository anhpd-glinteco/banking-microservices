package com.nnson128.account.security;

import com.nnson128.account.entity.User;
import com.nnson128.common.security.BankJwtConfiguration;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    public TokenService(RSAPublicKey publicKey, @Value("${SECURITY_PRIVATE_KEY}") String path) throws Exception {
        String pem = Files.readString(Path.of(path)).replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        if (!publicKey.getModulus().equals(privateKey.getModulus()) || publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA keys must match and contain at least 2048 bits");
        }
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new RSAKey.Builder(publicKey).privateKey(privateKey).build())));
    }
    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(BankJwtConfiguration.ISSUER).audience(List.of(BankJwtConfiguration.AUDIENCE))
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", List.of(user.getRole().name())).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
