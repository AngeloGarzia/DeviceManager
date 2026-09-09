package com.devicemanager.security.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit en mémoire (ConcurrentHashMap) — adapté à une seule instance JVM.
 */
@Component
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Deque<Long>> attemptsByKey = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            key = "unknown";
        }
        int safeLimit = Math.max(1, limit);
        long windowMs = Math.max(1L, window.toMillis());
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = attemptsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= safeLimit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
