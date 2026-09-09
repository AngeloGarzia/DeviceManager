package com.devicemanager.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAccountLockoutServiceTest {

    private MutableClock clock;
    private LoginAccountLockoutService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        service = new LoginAccountLockoutService(clock, 10, 15, 15);
    }

    @Test
    void locksAfterTenFailuresWithinWindow() {
        for (int i = 0; i < 9; i++) {
            service.recordFailure("Admin");
            assertThat(service.isLocked("admin")).isFalse();
        }
        service.recordFailure("admin");
        assertThat(service.isLocked("ADMIN")).isTrue();
        assertThatThrownBy(() -> service.assertNotLocked("admin"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).isEqualTo(LoginAccountLockoutService.LOCKED_MESSAGE);
                });
    }

    @Test
    void unlocksAfterLockDurationExpires() {
        for (int i = 0; i < 10; i++) {
            service.recordFailure("tech");
        }
        assertThat(service.isLocked("tech")).isTrue();

        clock.advance(15, ChronoUnit.MINUTES);
        assertThatCode(() -> service.assertNotLocked("tech")).doesNotThrowAnyException();
        assertThat(service.isLocked("tech")).isFalse();
    }

    @Test
    void resetClearsFailuresAndLock() {
        for (int i = 0; i < 10; i++) {
            service.recordFailure("admin");
        }
        assertThat(service.isLocked("admin")).isTrue();

        service.reset("admin");
        assertThat(service.isLocked("admin")).isFalse();
        assertThatCode(() -> service.assertNotLocked("admin")).doesNotThrowAnyException();

        for (int i = 0; i < 9; i++) {
            service.recordFailure("admin");
        }
        assertThat(service.isLocked("admin")).isFalse();
    }

    /** Clock de test mutable. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(long amount, ChronoUnit unit) {
            instant = instant.plus(amount, unit);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
