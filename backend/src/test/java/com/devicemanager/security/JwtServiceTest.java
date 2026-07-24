package com.devicemanager.security;

import com.devicemanager.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceTest.class);

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock private AppSettingsService appSettingsService;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L, appSettingsService);
        when(appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, 3_600_000L)).thenReturn(3_600_000L);
    }

    @Test
    void generateAndValidateToken() {
        log.info("Test JWT");
        String token = jwtService.generateToken("admin", Roles.ADMIN);

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(token, "admin")).isTrue();
        assertThat(jwtService.isTokenValid(token, "other")).isFalse();
        assertThat(jwtService.getExpirationMs()).isEqualTo(3_600_000L);
    }
}
