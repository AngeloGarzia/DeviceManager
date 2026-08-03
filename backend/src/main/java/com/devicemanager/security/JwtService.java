package com.devicemanager.security;

import com.devicemanager.service.AppSettingsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long defaultExpirationMs;
    private final AppSettingsService appSettingsService;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Lazy AppSettingsService appSettingsService) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET manquant. Définis une clé d'au moins 32 caractères dans les variables d'environnement.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET trop court (" + bytes.length + " octets). Minimum 32 caractères.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.defaultExpirationMs = expirationMs;
        this.appSettingsService = appSettingsService;
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        long expirationMs = appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, defaultExpirationMs);
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(username) && claims.getExpiration().after(new Date());
    }

    public long getExpirationMs() {
        return appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, defaultExpirationMs);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
