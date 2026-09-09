package com.devicemanager.security.ratelimit;

import java.time.Duration;

/**
 * Stockage abstrait des tentatives pour le rate limiting login.
 * <p>
 * Implémentation par défaut : {@link InMemoryRateLimitStore} (une instance JVM).
 * Pour un déploiement multi-instance, brancher une implémentation Redis, par exemple :
 * <pre>{@code
 * @Bean
 * @Primary
 * RateLimitStore rateLimitStore(StringRedisTemplate redis) {
 *     return new RedisRateLimitStore(redis); // à créer quand Redis est ajouté
 * }
 * }</pre>
 * Idée d’algo Redis : clé {@code login:rl:{ip}}, {@code INCR} + {@code EXPIRE} fenêtre 60s,
 * ou liste d’horodatages avec {@code ZADD}/{@code ZREMRANGEBYSCORE}.
 * Ne pas ajouter la dépendance Redis tant que l’app tourne sur une seule instance (Render).
 */
public interface RateLimitStore {

    /**
     * Enregistre une tentative et indique si elle est encore dans la limite.
     *
     * @param key   identifiant (ex. adresse IP)
     * @param limit nombre max d’événements dans la fenêtre
     * @param window durée de la fenêtre glissante
     * @return {@code true} si autorisé (tentative comptée), {@code false} si limite atteinte
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
