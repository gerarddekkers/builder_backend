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

    /**
     * New format: userId:username:role:expiresAt:signature
     */
    public String generateToken(long userId, String username, String role) {
        long expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_HOURS * 3600).getEpochSecond();
        String payload = userId + ":" + username + ":" + role + ":" + expiresAt;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Legacy format (backward compatible): username:role:expiresAt:signature
     */
    public String generateToken(String username, String role) {
        return generateToken(0L, username, role);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            // Support both old (4 parts) and new (5 parts) format
            if (parts.length != 4 && parts.length != 5) {
                return false;
            }

            long expiresAt;
            String signature;
            String payload;

            if (parts.length == 5) {
                // New format: userId:username:role:expiresAt:signature
                expiresAt = Long.parseLong(parts[3]);
                signature = parts[4];
                payload = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
            } else {
                // Old format: username:role:expiresAt:signature
                expiresAt = Long.parseLong(parts[2]);
                signature = parts[3];
                payload = parts[0] + ":" + parts[1] + ":" + parts[2];
            }

            if (Instant.now().getEpochSecond() > expiresAt) {
                return false;
            }

            String expectedSignature = sign(payload);
            return signature.equals(expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        String[] parts = decodeParts(token);
        if (parts != null && parts.length == 5) {
            return parts[0];
        }
        return null; // old format token has no userId
    }

    public String extractUsername(String token) {
        String[] parts = decodeParts(token);
        if (parts == null) return null;
        return parts.length == 5 ? parts[1] : parts[0];
    }

    public String extractRole(String token) {
        String[] parts = decodeParts(token);
        if (parts == null) return null;
        return parts.length == 5 ? parts[2] : parts[1];
    }

    private String[] decodeParts(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4 && parts.length != 5) {
                return null;
            }
            return parts;
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
