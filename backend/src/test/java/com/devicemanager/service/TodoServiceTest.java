package com.devicemanager.service;

import com.devicemanager.dto.TodoNettoyageCocheRequest;
import com.devicemanager.dto.TodoNettoyageHistoriqueItem;
import com.devicemanager.dto.TodoTacheLinkRequest;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.dto.TodoTacheStatusRequest;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.InterventionTechnique;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.TodoNettoyageMasLigne;
import com.devicemanager.entity.TodoRecurrence;
import com.devicemanager.entity.TodoRecurrenceFrequence;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.entity.TodoTacheType;
import com.devicemanager.entity.User;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoNettoyageMasLigneRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock private TodoTacheRepository todoTacheRepository;
    @Mock private TodoNettoyageMasLigneRepository todoNettoyageMasLigneRepository;
    @Mock private MasRepository masRepository;
    @Mock private InterventionTechniqueRepository interventionTechniqueRepository;
    @Mock private InterventionRepository interventionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @Mock private AtelierMemoirePublisher atelierMemoirePublisher;
    @Mock private TodoRecurrenceService todoRecurrenceService;
    @Mock private MissingRegleJeuxTodoService missingRegleJeuxTodoService;
    @Mock private StorageService storageService;
    @Mock private ImageOptimizationService imageOptimizationService;
    @Mock private Clock clock;
    @InjectMocks private TodoService todoService;

    private void stubClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-16T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void create_persistsOpenTask() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User user = TestFixtures.user("tech", "TECHNICIEN");
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(user));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> {
            TodoTache t = inv.getArgument(0);
            t.setId(11L);
            return t;
        });

        TodoTacheRequest request = new TodoTacheRequest();
        request.setTitre("Vérifier MAS-001");
        request.setDescription("Bruit anormal");
        request.setSeverite("HIGH");

        TodoTacheResponse response = todoService.create(request, List.of(), "tech");

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getStatut()).isEqualTo("OPEN");
        assertThat(response.getSeverite()).isEqualTo("HIGH");
        assertThat(response.getTitre()).isEqualTo("Vérifier MAS-001");
        assertThat(response.getType()).isEqualTo("USER");
    }

    @Test
    void changeStatus_marksDoneWithSignature() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User admin = TestFixtures.user("admin", "ADMIN");
        TodoTache entity = TodoTache.builder()
                .id(5L)
                .atelier(atelier)
                .titre("Tâche")
                .statut(TodoTacheStatut.OPEN)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(5L, atelier.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoTacheStatusRequest request = new TodoTacheStatusRequest();
        request.setStatut("DONE");
        request.setSignatureCloture(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        request.setSignataireClotureNom("Admin Test");
        request.setCommentaireCloture("Travail terminé sur site");

        TodoTacheResponse response = todoService.changeStatus(5L, request, "admin");

        assertThat(response.getStatut()).isEqualTo("DONE");
        assertThat(response.getCompletedByUsername()).isEqualTo("admin");
        assertThat(response.getSignatureCloture()).startsWith("data:image/");
        assertThat(response.getCommentaireCloture()).isEqualTo("Travail terminé sur site");
        assertThat(response.getDateCloture()).isNotNull();
        assertThat(response.getResponsableCloture()).isNotBlank();
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    void changeStatus_rejectsRecurringDoneMoreThan7DaysEarly() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User admin = TestFixtures.user("admin", "ADMIN");
        TodoRecurrence rule = TodoRecurrence.builder()
                .id(3L)
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .build();
        TodoTache entity = TodoTache.builder()
                .id(5L)
                .atelier(atelier)
                .titre("Hebdo")
                .statut(TodoTacheStatut.OPEN)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .recurrence(rule)
                .dueAt(LocalDateTime.of(2026, 9, 28, 8, 0)) // 12 jours après le 16
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(5L, atelier.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        TodoTacheStatusRequest request = new TodoTacheStatusRequest();
        request.setStatut("DONE");
        request.setSignatureCloture(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        assertThatThrownBy(() -> todoService.changeStatus(5L, request, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("7 jours");
                });
        verify(todoTacheRepository, never()).save(any());
    }

    @Test
    void changeStatus_allowsRecurringDoneWithin7DaysAndGeneratesNext() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User admin = TestFixtures.user("admin", "ADMIN");
        TodoRecurrence rule = TodoRecurrence.builder()
                .id(3L)
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .build();
        TodoTache entity = TodoTache.builder()
                .id(5L)
                .atelier(atelier)
                .titre("Hebdo")
                .statut(TodoTacheStatut.OPEN)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .recurrence(rule)
                .dueAt(LocalDateTime.of(2026, 9, 21, 8, 0)) // 5 jours après le 16
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(5L, atelier.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoTacheStatusRequest request = new TodoTacheStatusRequest();
        request.setStatut("DONE");
        request.setSignatureCloture(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        TodoTacheResponse response = todoService.changeStatus(5L, request, "admin");

        assertThat(response.getStatut()).isEqualTo("DONE");
        verify(todoRecurrenceService).generateDueOccurrences(atelier);
    }

    @Test
    void linkIntervention_attachesTechnique() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        TodoTache entity = TodoTache.builder()
                .id(5L)
                .atelier(atelier)
                .titre("Tâche")
                .statut(TodoTacheStatut.IN_PROGRESS)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .build();
        InterventionTechnique it = InterventionTechnique.builder()
                .id(90L)
                .visiteGroupeId("g1")
                .atelier(atelier)
                .mas(TestFixtures.mas())
                .motif("Réglage")
                .travaux("OK")
                .build();

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(5L, atelier.getId())).thenReturn(Optional.of(entity));
        when(interventionTechniqueRepository.findByIdAndAtelierId(90L, atelier.getId()))
                .thenReturn(Optional.of(it));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoTacheLinkRequest request = new TodoTacheLinkRequest();
        request.setInterventionTechniqueId(90L);

        TodoTacheResponse response = todoService.linkIntervention(5L, request, "tech");

        assertThat(response.getInterventionTechniqueId()).isEqualTo(90L);
        assertThat(entity.getInterventionTechnique()).isEqualTo(it);
        verify(todoTacheRepository).save(entity);
    }

    @Test
    void listPending_returnsOnlyActive() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByAtelierIdAndStatutIn(
                eq(atelier.getId()), any()))
                .thenReturn(List.of(TodoTache.builder()
                        .id(1L)
                        .atelier(atelier)
                        .titre("A")
                        .statut(TodoTacheStatut.OPEN)
                        .severite("LOW")
                        .createdByUsername("tech")
                        .build()));

        assertThat(todoService.listPending().getCount()).isEqualTo(1);
        verify(todoRecurrenceService).generateDueOccurrences();
    }

    @Test
    void listOverdueForReminder_delegatesToRepository() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 8, 0);
        TodoTache overdue = TodoTache.builder()
                .id(3L)
                .titre("Late")
                .statut(TodoTacheStatut.OPEN)
                .severite("HIGH")
                .dueAt(now.minusDays(2))
                .build();
        when(todoTacheRepository.findOverdueAcrossAteliers(any(), eq(now)))
                .thenReturn(List.of(overdue));

        assertThat(todoService.listOverdueForReminder(now)).containsExactly(overdue);
        verify(todoTacheRepository).findOverdueAcrossAteliers(any(), eq(now));
    }

    @Test
    void create_nettoyageMas_persistsChecklist() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User user = TestFixtures.user("tech", "TECHNICIEN");
        Mas mas1 = TestFixtures.mas();
        Mas mas2 = Mas.builder()
                .id(21L)
                .numero("MAS-002")
                .marque(TestFixtures.marque())
                .statut(mas1.getStatut())
                .utilise(true)
                .atelier(atelier)
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(user));
        when(masRepository.findByIdAndAtelierId(20L, atelier.getId())).thenReturn(Optional.of(mas1));
        when(masRepository.findByIdAndAtelierId(21L, atelier.getId())).thenReturn(Optional.of(mas2));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> {
            TodoTache t = inv.getArgument(0);
            t.setId(40L);
            return t;
        });

        TodoTacheRequest request = new TodoTacheRequest();
        request.setTitre("Nettoyage salle");
        request.setTypeTache("NETTOYAGE_MAS");
        request.setMasIds(List.of(20L, 21L));
        request.setSeverite("MEDIUM");

        TodoTacheResponse response = todoService.create(request, List.of(), "tech");

        assertThat(response.getId()).isEqualTo(40L);
        assertThat(response.getTypeTache()).isEqualTo("NETTOYAGE_MAS");
        assertThat(response.getMasId()).isNull();
        assertThat(response.getNettoyageTotalCount()).isEqualTo(2);
        assertThat(response.getNettoyageCocheCount()).isEqualTo(0);
        assertThat(response.getNettoyageLignes()).hasSize(2);
    }

    @Test
    void setNettoyageCoche_marksLineAndProgress() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User user = TestFixtures.user("tech", "TECHNICIEN");
        Mas mas = TestFixtures.mas();
        TodoTache entity = TodoTache.builder()
                .id(40L)
                .atelier(atelier)
                .titre("Nettoyage")
                .statut(TodoTacheStatut.OPEN)
                .typeTache(TodoTacheType.NETTOYAGE_MAS)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .nettoyageLignes(new ArrayList<>())
                .build();
        TodoNettoyageMasLigne ligne = TodoNettoyageMasLigne.builder()
                .id(1L)
                .todoTache(entity)
                .mas(mas)
                .coche(false)
                .build();
        entity.getNettoyageLignes().add(ligne);

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(40L, atelier.getId())).thenReturn(Optional.of(entity));
        when(todoNettoyageMasLigneRepository.findByTodoTacheIdAndMasId(40L, 20L))
                .thenReturn(Optional.of(ligne));
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(user));
        when(todoNettoyageMasLigneRepository.save(any(TodoNettoyageMasLigne.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoNettoyageCocheRequest req = new TodoNettoyageCocheRequest();
        req.setCoche(true);

        TodoTacheResponse response = todoService.setNettoyageCoche(40L, 20L, req, "tech");

        assertThat(ligne.isCoche()).isTrue();
        assertThat(ligne.getCocheAt()).isNotNull();
        assertThat(entity.getStatut()).isEqualTo(TodoTacheStatut.IN_PROGRESS);
        assertThat(response.getNettoyageCocheCount()).isEqualTo(1);
    }

    @Test
    void changeStatus_nettoyageDone_requiresAtLeastOneChecked() {
        Atelier atelier = TestFixtures.atelier();
        User admin = TestFixtures.user("admin", "ADMIN");
        Mas mas = TestFixtures.mas();
        TodoTache entity = TodoTache.builder()
                .id(40L)
                .atelier(atelier)
                .titre("Nettoyage")
                .statut(TodoTacheStatut.IN_PROGRESS)
                .typeTache(TodoTacheType.NETTOYAGE_MAS)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .nettoyageLignes(new ArrayList<>())
                .build();
        entity.getNettoyageLignes().add(TodoNettoyageMasLigne.builder()
                .todoTache(entity)
                .mas(mas)
                .coche(false)
                .build());

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(40L, atelier.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        TodoTacheStatusRequest request = new TodoTacheStatusRequest();
        request.setStatut("DONE");
        request.setSignatureCloture(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        request.setSignataireClotureNom("Admin");

        assertThatThrownBy(() -> todoService.changeStatus(40L, request, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("au moins une MAS");
                });
        verify(todoTacheRepository, never()).save(any());
    }

    @Test
    void changeStatus_nettoyageDone_partialOkWithSignature() {
        stubClock();
        Atelier atelier = TestFixtures.atelier();
        User admin = TestFixtures.user("admin", "ADMIN");
        Mas mas1 = TestFixtures.mas();
        Mas mas2 = Mas.builder()
                .id(21L)
                .numero("MAS-002")
                .marque(TestFixtures.marque())
                .statut(mas1.getStatut())
                .utilise(true)
                .atelier(atelier)
                .build();
        TodoTache entity = TodoTache.builder()
                .id(40L)
                .atelier(atelier)
                .titre("Nettoyage")
                .statut(TodoTacheStatut.IN_PROGRESS)
                .typeTache(TodoTacheType.NETTOYAGE_MAS)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .nettoyageLignes(new ArrayList<>())
                .build();
        entity.getNettoyageLignes().add(TodoNettoyageMasLigne.builder()
                .todoTache(entity)
                .mas(mas1)
                .coche(true)
                .cocheAt(LocalDateTime.of(2026, 9, 16, 9, 0))
                .cocheByUsername("tech")
                .build());
        entity.getNettoyageLignes().add(TodoNettoyageMasLigne.builder()
                .todoTache(entity)
                .mas(mas2)
                .coche(false)
                .build());

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoTacheRepository.findByIdAndAtelierId(40L, atelier.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoTacheStatusRequest request = new TodoTacheStatusRequest();
        request.setStatut("DONE");
        request.setSignatureCloture(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        request.setSignataireClotureNom("Admin Test");

        TodoTacheResponse response = todoService.changeStatus(40L, request, "admin");

        assertThat(response.getStatut()).isEqualTo("DONE");
        assertThat(response.getSignataireClotureNom()).isEqualTo("Admin Test");
        assertThat(response.getNettoyageCocheCount()).isEqualTo(1);
        assertThat(response.getNettoyageTotalCount()).isEqualTo(2);
    }

    @Test
    void freshnessScore_mapsDaysToZeroHundred() {
        assertThat(TodoService.freshnessScore(0, 90)).isEqualTo(100);
        assertThat(TodoService.freshnessScore(45, 90)).isEqualTo(50);
        assertThat(TodoService.freshnessScore(90, 90)).isEqualTo(0);
        assertThat(TodoService.freshnessScore(120, 90)).isEqualTo(0);
    }

    @Test
    void historiqueNettoyage_returnsCheckedLines() {
        Atelier atelier = TestFixtures.atelier();
        Mas mas = TestFixtures.mas();
        TodoTache tache = TodoTache.builder()
                .id(40L)
                .atelier(atelier)
                .titre("Nettoyage salle")
                .statut(TodoTacheStatut.DONE)
                .typeTache(TodoTacheType.NETTOYAGE_MAS)
                .severite("MEDIUM")
                .createdByUsername("tech")
                .completedAt(LocalDateTime.of(2026, 9, 16, 11, 0))
                .signataireClotureNom("Admin")
                .build();
        TodoNettoyageMasLigne ligne = TodoNettoyageMasLigne.builder()
                .todoTache(tache)
                .mas(mas)
                .coche(true)
                .cocheAt(LocalDateTime.of(2026, 9, 16, 10, 0))
                .cocheByDisplayName("Tech User")
                .build();

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(masRepository.findByIdAndAtelierId(20L, atelier.getId())).thenReturn(Optional.of(mas));
        when(todoNettoyageMasLigneRepository.findHistoriqueByAtelierAndMas(atelier.getId(), 20L))
                .thenReturn(List.of(ligne));

        List<TodoNettoyageHistoriqueItem> items = todoService.historiqueNettoyage(20L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTodoTitre()).isEqualTo("Nettoyage salle");
        assertThat(items.get(0).getCocheByDisplayName()).isEqualTo("Tech User");
        assertThat(items.get(0).getSignataireClotureNom()).isEqualTo("Admin");
    }
}