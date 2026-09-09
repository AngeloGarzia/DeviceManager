package com.devicemanager.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private InMemoryRateLimitStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryRateLimitStore();
    }

    @Test
    void allowsUpToLimitThenRejects() {
        Duration window = Duration.ofMinutes(1);
        assertThat(store.tryAcquire("1.2.3.4", 3, window)).isTrue();
        assertThat(store.tryAcquire("1.2.3.4", 3, window)).isTrue();
        assertThat(store.tryAcquire("1.2.3.4", 3, window)).isTrue();
        assertThat(store.tryAcquire("1.2.3.4", 3, window)).isFalse();
    }

    @Test
    void isolatesKeys() {
        Duration window = Duration.ofMinutes(1);
        assertThat(store.tryAcquire("a", 1, window)).isTrue();
        assertThat(store.tryAcquire("a", 1, window)).isFalse();
        assertThat(store.tryAcquire("b", 1, window)).isTrue();
    }
}
