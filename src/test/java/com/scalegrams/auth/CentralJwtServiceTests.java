package com.scalegrams.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;

class CentralJwtServiceTests {
    private KeyPair keys;
    private CentralJwtService service;
    private UUID subject;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        service = new CentralJwtService(pem);
        subject = UUID.randomUUID();
    }

    @Test
    void acceptsOnlyCentralAuthRsaTokensWithAudienceAndExpiry() {
        assertThat(service.subject(token("central-auth-service", "central-auth", subject.toString(), Instant.now().plusSeconds(60), true)))
                .isEqualTo(subject);
        assertThatThrownBy(() -> service.subject(token("other-issuer", "central-auth", subject.toString(), Instant.now().plusSeconds(60), true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subject(token("central-auth-service", "another-api", subject.toString(), Instant.now().plusSeconds(60), true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subject(token("central-auth-service", "central-auth", subject.toString(), Instant.now().minusSeconds(60), true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subject(token("central-auth-service", "central-auth", subject.toString(), null, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subject(token("central-auth-service", "central-auth", "not-a-uuid", Instant.now().plusSeconds(60), true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String token(String issuer, String audience, String sub, Instant expiresAt, boolean addExpiry) {
        var builder = Jwts.builder().issuer(issuer).subject(sub).audience().add(audience).and();
        if (addExpiry && expiresAt != null) builder.expiration(Date.from(expiresAt));
        return builder.signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
