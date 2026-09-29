package com.scalegrams.auth;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;

@Service
public class CentralJwtService {
    private final String publicKeyPem;

    public CentralJwtService(@Value("${app.auth.public-key-pem:}") String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public UUID subject(String token) {
        try {
            var parsed = Jwts.parser().verifyWith(publicKey()).requireIssuer("central-auth-service").build()
                    .parseSignedClaims(token);
            if (!"RS256".equals(parsed.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("El JWT central no usa RS256.");
            }
            if (parsed.getPayload().getExpiration() == null) {
                throw new IllegalArgumentException("El JWT central no tiene vencimiento.");
            }
            if (parsed.getPayload().getAudience() == null || !parsed.getPayload().getAudience().contains("central-auth")) {
                throw new IllegalArgumentException("El JWT central no está destinado a este servicio.");
            }
            String subject = parsed.getPayload().getSubject();
            return UUID.fromString(subject);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("El JWT central no tiene un subject UUID válido.", ex);
        }
    }

    private PublicKey publicKey() {
        try {
            if (publicKeyPem == null || publicKeyPem.isBlank()) {
                throw new IllegalArgumentException("AUTH_PUBLIC_KEY_PEM no está configurada.");
            }
            String encoded = publicKeyPem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception ex) {
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM debe contener una clave pública RSA PKIX PEM válida.", ex);
        }
    }
}
