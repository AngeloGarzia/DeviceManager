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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service de création et validation des jetons JWT (HS256) et des refresh tokens opaques.
 * <p>
 * La clé secrète provient de {@code app.jwt.secret} ({@code APP_JWT_SECRET}, minimum 32 octets).
 * La durée d'accès peut être surchargée via les paramètres applicatifs en base
 * ({@link AppSettingsService#JWT_EXPIRATION_MS}).
 */
@Component
public final class JwtService {

    private final SecretKey key;
    private final long defaultAccessExpirationMs;
    private final long refreshExpirationMs;
    private final AppSettingsService appSettingsService;

    /**
     * Initialise le service avec la clé secrète et les durées d'expiration.
     *
     * @param secret                 clé HMAC (au moins 32 caractères)
     * @param accessExpirationMs     durée de validité du jeton d'accès (ms)
     * @param refreshExpirationMs    durée de validité du refresh token (ms)
     * @param appSettingsService     service de paramètres (injection paresseuse pour éviter les cycles)
     * @throws IllegalStateException si la clé est absente ou trop courte
     */
    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
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
        this.defaultAccessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.appSettingsService = appSettingsService;
    }

    /**
     * Alias de {@link #generateAccessToken(String, String)} (compatibilité tests / anciens appels).
     */
    public String generateToken(String username, String role) {
        return generateAccessToken(username, role);
    }

    /**
     * Génère un JWT d'accès signé (durée courte).
     *
     * @param username nom d'utilisateur (subject du token)
     * @param role     rôle applicatif (claim {@code role})
     * @return token JWT compact
     */
    public String generateAccessToken(String username, String role) {
        Date now = new Date();
        long expirationMs = appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, defaultAccessExpirationMs);
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
     * Génère une valeur opaque de refresh token (UUID aléatoire).
     *
     * @return valeur brute à envoyer au client (cookie) — ne pas stocker en clair
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    /**
     * Hash SHA-256 hexadécimal d'un jeton opaque pour stockage en base.
     *
     * @param rawToken valeur brute
     * @return hash hex (64 caractères)
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 non disponible", ex);
        }
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
     * Durée de validité effective des jetons d'accès (paramètre applicatif ou valeur par défaut).
     *
     * @return durée en millisecondes
     */
    public long getExpirationMs() {
        return appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, defaultAccessExpirationMs);
    }

    /**
     * Durée de validité des refresh tokens.
     *
     * @return durée en millisecondes
     */
    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
