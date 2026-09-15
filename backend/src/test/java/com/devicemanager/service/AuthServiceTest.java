package com.devicemanager.service;

import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.dto.MessageResponse;
import com.devicemanager.entity.PasswordResetToken;
import com.devicemanager.entity.RefreshToken;
import com.devicemanager.entity.User;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.mail.templates.PasswordResetEmail;
import com.devicemanager.repository.PasswordResetTokenRepository;
import com.devicemanager.repository.RefreshTokenRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import com.devicemanager.security.LoginAccountLockoutService;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceTest.class);

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AtelierService atelierService;
    @Mock private LoginAccountLockoutService accountLockoutService;
    @Mock private TransactionalMail transactionalMail;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setFrontendUrl() {
        ReflectionTestUtils.setField(authService, "frontendBaseUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(authService, "corsAllowedOrigins", "");
    }

    @Test
    void login_success() {
        log.info("Test login success");
        var user = TestFixtures.user("admin", Roles.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(atelierService.listForUser("admin")).thenReturn(List.of(
                AtelierSummary.builder().id(100L).nom("Atelier Balaruc").label("Atelier Balaruc — Balaruc").build()
        ));
        when(jwtService.generateAccessToken("admin", Roles.ADMIN)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(900_000L);
        when(jwtService.generateRefreshTokenValue()).thenReturn("refresh-raw");
        when(jwtService.hashToken("refresh-raw")).thenReturn("refresh-hash");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthService.AuthSession session = authService.login(request);
        AuthResponse response = session.response();

        assertThat(session.refreshToken()).isEqualTo("refresh-raw");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getAtelierId()).isEqualTo(100L);
        assertThat(response.getGroupeNom()).isEqualTo("Circus");
        assertThat(response.getMustChangePassword()).isFalse();

        verify(accountLockoutService).assertNotLocked("admin");
        verify(accountLockoutService).reset("admin");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("refresh-hash");
    }

    @Test
    void login_usesPreferredAtelierWhenStillAllowed() {
        var preferred = TestFixtures.atelier();
        preferred.setId(200L);
        preferred.setNom("Atelier Prefere");
        var user = TestFixtures.user("admin", Roles.ADMIN);
        user.setPreferredAtelier(preferred);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(atelierService.listForUser("admin")).thenReturn(List.of(
                AtelierSummary.builder().id(100L).nom("Autre").label("Autre").build(),
                AtelierSummary.builder().id(200L).nom("Atelier Prefere").label("Atelier Prefere").build()
        ));
        stubTokenIssuance("admin", Roles.ADMIN);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthResponse response = authService.login(request).response();

        assertThat(response.getAtelierId()).isEqualTo(200L);
    }

    @Test
    void login_fallsBackWhenPreferredAtelierNoLongerAllowed() {
        var preferred = TestFixtures.atelier();
        preferred.setId(999L);
        var user = TestFixtures.user("admin", Roles.ADMIN);
        user.setPreferredAtelier(preferred);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(atelierService.listForUser("admin")).thenReturn(List.of(
                AtelierSummary.builder().id(100L).nom("Atelier Balaruc").label("Atelier Balaruc").build()
        ));
        stubTokenIssuance("admin", Roles.ADMIN);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthResponse response = authService.login(request).response();

        assertThat(response.getAtelierId()).isEqualTo(100L);
    }

    @Test
    void login_rejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setUsername("ghost");
        request.setPassword("x");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).isEqualTo("Identifiants invalides");
                });
        verify(accountLockoutService).recordFailure("ghost");
    }

    @Test
    void login_rejectsBadPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("bad");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Identifiants invalides");
        verify(accountLockoutService).recordFailure("admin");
    }

    @Test
    void login_rejectsWhenAccountLocked() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, LoginAccountLockoutService.LOCKED_MESSAGE))
                .when(accountLockoutService).assertNotLocked("admin");

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).isEqualTo(LoginAccountLockoutService.LOCKED_MESSAGE);
                });
    }

    @Test
    void requestPasswordReset_unknownEmailStillReturnsGenericSuccess() {
        when(userRepository.findByEmailIgnoreCase("ghost@test.local")).thenReturn(Optional.empty());

        MessageResponse response = authService.requestPasswordReset("ghost@test.local");

        assertThat(response.getMessage()).isEqualTo(AuthService.FORGOT_PASSWORD_MESSAGE);
        verify(passwordResetTokenRepository, never()).save(any());
        verify(transactionalMail, never()).sendPasswordReset(anyString(), any());
    }

    @Test
    void requestPasswordReset_sendsMailWhenUserExists() {
        User user = TestFixtures.user("tech", Roles.TECHNICIEN);
        when(userRepository.findByEmailIgnoreCase("tech@test.local")).thenReturn(Optional.of(user));
        when(jwtService.generateRefreshTokenValue()).thenReturn("reset-raw");
        when(jwtService.hashToken("reset-raw")).thenReturn("reset-hash");
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionalMail.sendPasswordReset(anyString(), any())).thenReturn(EmailSendResult.success());

        MessageResponse response = authService.requestPasswordReset("tech@test.local");

        assertThat(response.getMessage()).isEqualTo(AuthService.FORGOT_PASSWORD_MESSAGE);
        verify(passwordResetTokenRepository).invalidateUnusedByUserId(50L);
        ArgumentCaptor<PasswordResetEmail.Context> ctx =
                ArgumentCaptor.forClass(PasswordResetEmail.Context.class);
        verify(transactionalMail).sendPasswordReset(eq("tech@test.local"), ctx.capture());
        assertThat(ctx.getValue().resetUrl())
                .isEqualTo("http://localhost:4200/reset-password?token=reset-raw");
    }

    @Test
    void resetPassword_rejectsInvalidToken() {
        when(jwtService.hashToken("bad")).thenReturn("bad-hash");
        when(passwordResetTokenRepository.findActiveByTokenHash("bad-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad", "newpass12"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("invalide ou expiré");
                });
    }

    @Test
    void resetPassword_rejectsExpiredToken() {
        User user = TestFixtures.user("tech", Roles.TECHNICIEN);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(60))
                .used(false)
                .build();
        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findActiveByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("raw", "newpass12"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void resetPassword_updatesPasswordAndRevokesSessions() {
        User user = TestFixtures.user("tech", Roles.TECHNICIEN);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findActiveByTokenHash("hash")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("newpass12", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("newpass12")).thenReturn("encoded-new");

        authService.resetPassword("raw", "newpass12");

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllByUserId(50L);
        verify(userRepository).save(user);
    }

    private void stubTokenIssuance(String username, String role) {
        when(jwtService.generateAccessToken(username, role)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(900_000L);
        when(jwtService.generateRefreshTokenValue()).thenReturn("refresh-raw");
        when(jwtService.hashToken("refresh-raw")).thenReturn("refresh-hash");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
