package com.devicemanager.service;

import com.devicemanager.dto.UserRequest;
import com.devicemanager.dto.UserResponse;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Logger log = LoggerFactory.getLogger(UserServiceTest.class);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AtelierService atelierService;
    @InjectMocks private UserService userService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    @Test
    void create_success() {
        log.info("Test create user");
        when(userRepository.existsByUsername("tech2")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(60L);
            return u;
        });

        UserRequest request = new UserRequest();
        request.setUsername(" tech2 ");
        request.setNom("Martin");
        request.setPrenom("Alice");
        request.setEmail(" Alice.Martin@Casino.local ");
        request.setPassword("secret1");
        request.setRole("TECH");

        UserResponse response = userService.create(request);

        assertThat(response.getUsername()).isEqualTo("tech2");
        assertThat(response.getNom()).isEqualTo("Martin");
        assertThat(response.getPrenom()).isEqualTo("Alice");
        assertThat(response.getEmail()).isEqualTo("alice.martin@casino.local");
        assertThat(response.getRole()).isEqualTo(Roles.TECHNICIEN);
    }

    @Test
    void create_rejectsDuplicateUsername() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        UserRequest request = new UserRequest();
        request.setUsername("admin");
        request.setNom("Admin");
        request.setPrenom("Sys");
        request.setEmail("admin@test.local");
        request.setPassword("secret1");
        request.setRole("ADMIN");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Nom d'utilisateur déjà utilisé");
    }

    @Test
    void create_rejectsDuplicateEmail() {
        when(userRepository.existsByUsername("tech3")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@test.local")).thenReturn(true);

        UserRequest request = new UserRequest();
        request.setUsername("tech3");
        request.setNom("Martin");
        request.setPrenom("Alice");
        request.setEmail("alice@test.local");
        request.setPassword("secret1");
        request.setRole("TECHNICIEN");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("E-mail déjà utilisé");
    }

    @Test
    void create_requiresPassword() {
        when(userRepository.existsByUsername("x")).thenReturn(false);

        UserRequest request = new UserRequest();
        request.setUsername("x");
        request.setNom("X");
        request.setPrenom("Y");
        request.setEmail("x@test.local");
        request.setRole("ADMIN");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mot de passe obligatoire");
    }

    @Test
    void delete_rejectsSelf() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));

        assertThatThrownBy(() -> userService.delete(50L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Vous ne pouvez pas supprimer votre propre compte");
    }

    @Test
    void delete_rejectsLastAdmin() {
        User admin = TestFixtures.user("admin", Roles.ADMIN);
        when(userRepository.findById(50L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> userService.delete(50L, "other"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Impossible de supprimer le dernier administrateur");
                });
    }

    @Test
    void create_rejectsInvalidRole() {
        when(userRepository.existsByUsername("x")).thenReturn(false);

        UserRequest request = new UserRequest();
        request.setUsername("x");
        request.setNom("X");
        request.setPrenom("Y");
        request.setEmail("x@test.local");
        request.setPassword("secret1");
        request.setRole("GUEST");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Rôle invalide (ADMIN ou TECHNICIEN)");
    }
}
