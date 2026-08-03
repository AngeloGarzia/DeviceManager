package com.devicemanager.service;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.entity.Atelier;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import com.devicemanager.tenancy.AtelierContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtelierServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AtelierServiceTest.class);

    @Mock private AtelierRepository atelierRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AtelierService atelierService;

    @AfterEach
    void clearContext() {
        AtelierContext.clear();
    }

    @Test
    void listForUser_returnsAteliersOfGroupe() {
        log.info("Test list ateliers");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        when(atelierRepository.findAllByGroupeId(1L)).thenReturn(List.of(TestFixtures.atelier()));

        List<AtelierSummary> list = atelierService.listForUser("admin");

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getLabel()).contains("Atelier Balaruc");
    }

    @Test
    void requireCurrentAtelier_requiresHeader() {
        assertThatThrownBy(() -> atelierService.requireCurrentAtelier())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Sélectionnez un atelier (en-tête X-Atelier-Id)");
    }

    @Test
    void requireCurrentAtelier_returnsEntity() {
        AtelierContext.set(100L);
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(TestFixtures.atelier()));

        Atelier atelier = atelierService.requireCurrentAtelier();

        assertThat(atelier.getNom()).isEqualTo("Atelier Balaruc");
    }

    @Test
    void listForUser_unknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atelierService.listForUser("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Utilisateur introuvable");
    }

    @Test
    void setPreferredAtelier_persistsOnUser() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        var atelier = TestFixtures.atelier();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(atelier));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AtelierSummary summary = atelierService.setPreferredAtelier("admin", 100L);

        assertThat(summary.getId()).isEqualTo(100L);
        assertThat(user.getPreferredAtelier()).isEqualTo(atelier);
        verify(userRepository).save(user);
    }
}
