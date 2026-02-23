package com.mentesme.builder.service;

import com.mentesme.builder.config.AuthProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {

    private static final long TOKEN_VALIDITY_HOURS = 24;
    private final AuthProperties authProperties;

    public TokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String generateToken(String username, String role) {
        long expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_HOURS * 3600).getEpochSecond();
        String payload = username + ":" + role + ":" + expiresAt;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                return false;
            }
            String username = parts[0];
            String role = parts[1];
            long expiresAt = Long.parseLong(parts[2]);
            String signature = parts[3];

            // Check expiration
            if (Instant.now().getEpochSecond() > expiresAt) {
                return false;
            }

            // Verify signature
            String expectedSignature = sign(username + ":" + role + ":" + expiresAt);
            return signature.equals(expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                return null;
            }
            return parts[0];
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                return null;
            }
            return parts[1];
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    authProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }
}
