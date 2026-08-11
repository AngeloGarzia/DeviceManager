package com.devicemanager.service;

import com.devicemanager.dto.AtelierRequest;
import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.coordonnees.AdressePostaleDto;
import com.devicemanager.dto.coordonnees.EmailCoordDto;
import com.devicemanager.dto.coordonnees.TelephoneCoordDto;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.User;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.CasinoRepository;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.SfmRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtelierServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AtelierServiceTest.class);

    @Mock private AtelierRepository atelierRepository;
    @Mock private CasinoRepository casinoRepository;
    @Mock private UserRepository userRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private MasRepository masRepository;
    @Mock private SfmRepository sfmRepository;
    @Mock private CommandeRepository commandeRepository;
    @Mock private InterventionRepository interventionRepository;
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
        when(userRepository.findAllByPreferredAtelierId(100L)).thenReturn(List.of());

        List<AtelierSummary> list = atelierService.listForUser("admin");

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getLabel()).contains("Atelier Balaruc");
        assertThat(list.getFirst().getUtilisateursPreferes()).isEmpty();
    }

    @Test
    void requireCurrentAtelier_requiresHeader() {
        assertThatThrownBy(() -> atelierService.requireCurrentAtelier())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Sélectionnez un atelier pour continuer.");
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
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByPreferredAtelierId(100L)).thenReturn(List.of());

        AtelierSummary summary = atelierService.setPreferredAtelier("admin", 100L);

        assertThat(summary.getId()).isEqualTo(100L);
        assertThat(user.getPreferredAtelier()).isEqualTo(atelier);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void setPreferredAtelier_rejectsForeignGroupe() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        var otherGroupe = com.devicemanager.entity.Groupe.builder().id(2L).nom("Autre").build();
        var otherCasino = Casino.builder().id(11L).nom("X").groupe(otherGroupe).build();
        var foreign = Atelier.builder().id(999L).nom("Foreign").casino(otherCasino).build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(atelierRepository.findByIdWithCasino(999L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> atelierService.setPreferredAtelier("admin", 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Atelier non autorisé pour ce compte");
    }

    @Test
    void setPreferredAtelier_notFound() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        when(atelierRepository.findByIdWithCasino(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atelierService.setPreferredAtelier("admin", 404L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Atelier introuvable");
    }

    @Test
    void listForUser_emptyWhenNoGroupe() {
        var user = TestFixtures.user("orphan", Roles.TECHNICIEN);
        user.setGroupe(null);
        when(userRepository.findByUsername("orphan")).thenReturn(Optional.of(user));

        assertThat(atelierService.listForUser("orphan")).isEmpty();
    }

    @Test
    void listForUser_technicienOnlyPreferred() {
        var tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        tech.setPreferredAtelier(TestFixtures.atelier());
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(TestFixtures.atelier()));
        when(userRepository.findAllByPreferredAtelierId(100L)).thenReturn(List.of(tech));

        List<AtelierSummary> list = atelierService.listForUser("tech");

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getId()).isEqualTo(100L);
    }

    @Test
    void setPreferredAtelier_rejectsTechnicien() {
        when(userRepository.findByUsername("tech"))
                .thenReturn(Optional.of(TestFixtures.user("tech", Roles.TECHNICIEN)));

        assertThatThrownBy(() -> atelierService.setPreferredAtelier("tech", 100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Seuls les administrateurs peuvent changer d'atelier");
    }

    @Test
    void create_persistsCoordonneesAndResponsables() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        user.setId(1L);
        var casino = TestFixtures.atelier().getCasino();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(casinoRepository.findByIdWithGroupe(casino.getId())).thenReturn(Optional.of(casino));
        when(atelierRepository.findByNomIgnoreCaseAndCasinoId("Nouvel atelier", casino.getId()))
                .thenReturn(Optional.empty());
        when(userRepository.findAllByIdInWithGroupe(anyCollection())).thenReturn(List.of(user));
        when(atelierRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Atelier a = inv.getArgument(0);
            a.setId(200L);
            return a;
        });
        when(userRepository.findAllByPreferredAtelierId(200L)).thenReturn(List.of(user));

        AtelierRequest req = new AtelierRequest();
        req.setNom("  Nouvel atelier  ");
        req.setCasinoId(casino.getId());
        AdressePostaleDto adresse = AdressePostaleDto.builder()
                .ligne1("1 rue du Casino")
                .codePostal("34540")
                .ville("Balaruc")
                .pays("France")
                .build();
        req.setAdresse(adresse);
        EmailCoordDto email = new EmailCoordDto();
        email.setValeur("atelier@example.com");
        email.setPrincipal(true);
        req.setEmails(List.of(email));
        TelephoneCoordDto tel = new TelephoneCoordDto();
        tel.setValeur("0467000000");
        tel.setLabel("Standard");
        tel.setPrincipal(true);
        req.setTelephones(List.of(tel));
        req.setResponsableIds(List.of(1L));
        req.setUtilisateurPrefereIds(List.of(1L));

        AtelierSummary created = atelierService.create("admin", req);

        assertThat(created.getId()).isEqualTo(200L);
        assertThat(created.getNom()).isEqualTo("Nouvel atelier");
        assertThat(created.getCoordonnees()).isNotNull();
        assertThat(created.getCoordonnees().getAdresse().getVille()).isEqualTo("Balaruc");
        assertThat(created.getCoordonnees().getEmails()).hasSize(1);
        assertThat(created.getCoordonnees().getTelephones()).hasSize(1);
        assertThat(created.getResponsables()).hasSize(1);
        assertThat(created.getResponsables().getFirst().getId()).isEqualTo(1L);
        assertThat(created.getUtilisateursPreferes()).hasSize(1);
        assertThat(created.getUtilisateursPreferes().getFirst().getId()).isEqualTo(1L);
        verify(userRepository).saveAll(any());
    }

    @Test
    void create_rejectsResponsableOutsideGroupe() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        user.setId(1L);
        var casino = TestFixtures.atelier().getCasino();
        var foreign = TestFixtures.user("other", Roles.TECHNICIEN);
        foreign.setId(99L);
        foreign.setGroupe(com.devicemanager.entity.Groupe.builder().id(2L).nom("Autre").build());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(casinoRepository.findByIdWithGroupe(casino.getId())).thenReturn(Optional.of(casino));
        when(atelierRepository.findByNomIgnoreCaseAndCasinoId("Atelier X", casino.getId()))
                .thenReturn(Optional.empty());
        when(userRepository.findAllByIdInWithGroupe(anyCollection())).thenReturn(List.of(foreign));

        AtelierRequest req = new AtelierRequest();
        req.setNom("Atelier X");
        req.setCasinoId(casino.getId());
        req.setResponsableIds(List.of(99L));

        assertThatThrownBy(() -> atelierService.create("admin", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Responsable hors du groupe de l'atelier");
    }

    @Test
    void delete_rejectsWhenDataExists() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        var atelier = TestFixtures.atelier();
        atelier.setResponsables(new HashSet<>());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(atelier));
        when(deviceRepository.countByAtelierId(100L)).thenReturn(1L);
        when(masRepository.countByAtelierId(100L)).thenReturn(0L);
        when(sfmRepository.countByAtelierId(100L)).thenReturn(0L);
        when(commandeRepository.countByAtelierId(100L)).thenReturn(0L);

        assertThatThrownBy(() -> atelierService.delete("admin", 100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Impossible de supprimer");
    }

    @Test
    void delete_clearsPreferredAtelierThenDeletes() {
        var user = TestFixtures.user("admin", Roles.ADMIN);
        var atelier = TestFixtures.atelier();
        atelier.setResponsables(new HashSet<>());
        user.setPreferredAtelier(atelier);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(atelier));
        when(deviceRepository.countByAtelierId(100L)).thenReturn(0L);
        when(masRepository.countByAtelierId(100L)).thenReturn(0L);
        when(sfmRepository.countByAtelierId(100L)).thenReturn(0L);
        when(commandeRepository.countByAtelierId(100L)).thenReturn(0L);
        when(interventionRepository.countByAtelierId(100L)).thenReturn(0L);
        when(userRepository.clearPreferredAtelier(100L)).thenReturn(1);

        atelierService.delete("admin", 100L);

        verify(userRepository).clearPreferredAtelier(100L);
        verify(atelierRepository).delete(atelier);
        verify(atelierRepository).flush();
    }
}
