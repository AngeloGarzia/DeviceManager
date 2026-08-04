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

/**
 * Service de création et validation des jetons JWT (HS256).
 * <p>
 * La clé secrète provient de {@code app.jwt.secret} ({@code APP_JWT_SECRET}, minimum 32 octets).
 * La durée de validité peut être surchargée via les paramètres applicatifs en base
 * ({@link AppSettingsService#JWT_EXPIRATION_MS}).
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long defaultExpirationMs;
    private final AppSettingsService appSettingsService;

    /**
     * Initialise le service avec la clé secrète et la durée d'expiration par défaut.
     *
     * @param secret              clé HMAC (au moins 32 caractères)
     * @param expirationMs        durée de validité par défaut en millisecondes
     * @param appSettingsService  service de paramètres (injection paresseuse pour éviter les cycles)
     * @throws IllegalStateException si la clé est absente ou trop courte
     */
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

    /**
     * Génère un JWT signé pour l'utilisateur et son rôle.
     *
     * @param username nom d'utilisateur (subject du token)
     * @param role     rôle applicatif (claim {@code role})
     * @return token JWT compact
     */
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

    /**
     * Extrait le nom d'utilisateur (subject) d'un token.
     *
     * @param token JWT à parser
     * @return subject du token
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Vérifie que le token correspond à l'utilisateur et n'est pas expiré.
     *
     * @param token    JWT à valider
     * @param username nom d'utilisateur attendu
     * @return {@code true} si le token est valide pour cet utilisateur
     */
    public boolean isTokenValid(String token, String username) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(username) && claims.getExpiration().after(new Date());
    }

    /**
     * Durée de validité effective des tokens (paramètre applicatif ou valeur par défaut).
     *
     * @return durée en millisecondes
     */
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
