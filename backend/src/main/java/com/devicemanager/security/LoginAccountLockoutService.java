package com.devicemanager.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verrouillage temporaire d’un compte après trop d’échecs de login consécutifs
 * (indépendamment de l’IP — complète le rate limit par IP).
 */
@Service
public class LoginAccountLockoutService {

    public static final String LOCKED_MESSAGE =
            "Compte temporairement verrouillé, réessayez plus tard";

    private final Clock clock;
    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockDuration;

    private final Map<String, Deque<Long>> failuresByUsername = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntilEpochMs = new ConcurrentHashMap<>();

    public LoginAccountLockoutService(
            Clock clock,
            @Value("${app.security.login-account-max-failures:10}") int maxFailures,
            @Value("${app.security.login-account-failure-window-minutes:15}") int failureWindowMinutes,
            @Value("${app.security.login-account-lock-minutes:15}") int lockMinutes) {
        this.clock = clock;
        this.maxFailures = Math.max(1, maxFailures);
        this.failureWindow = Duration.ofMinutes(Math.max(1, failureWindowMinutes));
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    /** Normalise le username pour le compteur (trim + lower-case). */
    public static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Refuse immédiatement si le compte (ou la chaîne username) est verrouillé.
     */
    public void assertNotLocked(String username) {
        String key = normalizeUsername(username);
        if (key.isEmpty()) {
            return;
        }
        Long until = lockedUntilEpochMs.get(key);
        if (until == null) {
            return;
        }
        long now = clock.millis();
        if (now < until) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, LOCKED_MESSAGE);
        }
        lockedUntilEpochMs.remove(key, until);
    }

    /** Enregistre un échec ; verrouille si le seuil est atteint dans la fenêtre. */
    public void recordFailure(String username) {
        String key = normalizeUsername(username);
        if (key.isEmpty()) {
            return;
        }
        long now = clock.millis();
        long windowMs = failureWindow.toMillis();
        Deque<Long> timestamps = failuresByUsername.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() >= maxFailures) {
                lockedUntilEpochMs.put(key, now + lockDuration.toMillis());
                timestamps.clear();
            }
        }
    }

    /** Réinitialise échecs et verrou après une connexion réussie. */
    public void reset(String username) {
        String key = normalizeUsername(username);
        if (key.isEmpty()) {
            return;
        }
        failuresByUsername.remove(key);
        lockedUntilEpochMs.remove(key);
    }

    /** Visible pour les tests. */
    boolean isLocked(String username) {
        String key = normalizeUsername(username);
        Long until = lockedUntilEpochMs.get(key);
        return until != null && clock.millis() < until;
    }
}
