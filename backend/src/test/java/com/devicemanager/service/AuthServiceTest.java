package com.devicemanager.service;

import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceTest.class);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AtelierService atelierService;
    @InjectMocks private AuthService authService;

    @Test
    void login_success() {
        log.info("Test login success");
        var user = TestFixtures.user("admin", Roles.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(atelierService.listForUser("admin")).thenReturn(List.of(
                AtelierSummary.builder().id(100L).nom("Atelier Balaruc").label("Atelier Balaruc — Balaruc").build()
        ));
        when(jwtService.generateToken("admin", Roles.ADMIN)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getAtelierId()).isEqualTo(100L);
        assertThat(response.getGroupeNom()).isEqualTo("Circus");
    }

    @Test
    void login_usesPreferredAtelierWhenStillAllowed() {
        var preferred = TestFixtures.atelier();
        preferred.setId(200L);
        preferred.setNom("Atelier Préféré");
        var user = TestFixtures.user("admin", Roles.ADMIN);
        user.setPreferredAtelier(preferred);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(atelierService.listForUser("admin")).thenReturn(List.of(
                AtelierSummary.builder().id(100L).nom("Autre").label("Autre").build(),
                AtelierSummary.builder().id(200L).nom("Atelier Préféré").label("Atelier Préféré").build()
        ));
        when(jwtService.generateToken("admin", Roles.ADMIN)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthResponse response = authService.login(request);

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
        when(jwtService.generateToken("admin", Roles.ADMIN)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AuthResponse response = authService.login(request);

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
    }
}
