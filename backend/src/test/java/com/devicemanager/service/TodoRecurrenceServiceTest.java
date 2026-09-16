package com.devicemanager.service;

import com.devicemanager.entity.TodoRecurrence;
import com.devicemanager.entity.TodoRecurrenceFrequence;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoRecurrenceRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoRecurrenceServiceTest {

    @Mock private TodoRecurrenceRepository todoRecurrenceRepository;
    @Mock private TodoTacheRepository todoTacheRepository;
    @Mock private MasRepository masRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @Mock private Clock clock;
    @InjectMocks private TodoRecurrenceService service;

    private TodoRecurrence weekly;

    @BeforeEach
    void setUp() {
        weekly = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1) // lundi
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 14)) // lundi
                .build();
    }

    private void stubClock(String isoInstant) {
        when(clock.instant()).thenReturn(Instant.parse(isoInstant));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void computeFirstDue_weeklyAlignsToJourSemaine() {
        TodoRecurrence fromTuesday = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .heureDue(LocalTime.of(9, 0))
                .dateDebut(LocalDate.of(2026, 9, 15)) // mardi
                .build();
        LocalDateTime due = service.computeFirstDue(fromTuesday);
        assertThat(due.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 21)); // lundi suivant
        assertThat(due.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void advance_intervalDays() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.INTERVAL_DAYS)
                .intervalDays(3)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 1))
                .build();
        LocalDateTime next = service.advance(rule, LocalDateTime.of(2026, 9, 1, 8, 0));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 9, 4, 8, 0));
    }

    @Test
    void advance_monthly() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.MONTHLY)
                .jourMois(15)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 1, 15))
                .build();
        LocalDateTime next = service.advance(rule, LocalDateTime.of(2026, 1, 15, 8, 0));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 15, 8, 0));
    }

    @Test
    void occurrenceKey_isoDate() {
        assertThat(TodoRecurrenceService.occurrenceKey(LocalDateTime.of(2026, 9, 16, 8, 0)))
                .isEqualTo("2026-09-16");
    }

    @Test
    void computeFirstDue_weeklySameDay() {
        LocalDateTime due = service.computeFirstDue(weekly);
        assertThat(due).isEqualTo(LocalDateTime.of(2026, 9, 14, 8, 0));
    }

    @Test
    void generateDueOccurrences_createsWeeklyWithin7DaysEarly() {
        // Mercredi 9 sept → lundi 14 sept = 5 jours → dans la fenêtre.
        stubClock("2026-09-09T10:00:00Z");
        var atelier = TestFixtures.atelier();
        TodoRecurrence rule = TodoRecurrence.builder()
                .id(1L)
                .atelier(atelier)
                .titre("Contrôle hebdo")
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 14))
                .active(true)
                .prochaineEcheance(LocalDateTime.of(2026, 9, 14, 8, 0))
                .createdByUsername("admin")
                .build();
        when(todoRecurrenceRepository.findActiveByAtelierId(atelier.getId())).thenReturn(List.of(rule));
        when(todoTacheRepository.existsByRecurrenceIdAndOccurrenceKey(1L, "2026-09-14")).thenReturn(false);
        when(todoTacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(todoRecurrenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateDueOccurrences(atelier);

        ArgumentCaptor<TodoTache> captor = ArgumentCaptor.forClass(TodoTache.class);
        verify(todoTacheRepository).save(captor.capture());
        assertThat(captor.getValue().getDueAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 8, 0));
        assertThat(rule.getProchaineEcheance()).isEqualTo(LocalDateTime.of(2026, 9, 21, 8, 0));
    }

    @Test
    void generateDueOccurrences_skipsWeeklyMoreThan7DaysEarly() {
        // Dimanche 6 sept → lundi 14 sept = 8 jours → hors fenêtre.
        stubClock("2026-09-06T10:00:00Z");
        var atelier = TestFixtures.atelier();
        TodoRecurrence rule = TodoRecurrence.builder()
                .id(1L)
                .atelier(atelier)
                .titre("Contrôle hebdo")
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 14))
                .active(true)
                .prochaineEcheance(LocalDateTime.of(2026, 9, 14, 8, 0))
                .createdByUsername("admin")
                .build();
        when(todoRecurrenceRepository.findActiveByAtelierId(atelier.getId())).thenReturn(List.of(rule));
        when(todoRecurrenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateDueOccurrences(atelier);

        verify(todoTacheRepository, never()).save(any());
        assertThat(rule.getProchaineEcheance()).isEqualTo(LocalDateTime.of(2026, 9, 14, 8, 0));
    }

    @Test
    void allowsEarlyCompletion_weeklyAndMonthlyOnly() {
        assertThat(TodoRecurrenceService.allowsEarlyCompletion(TodoRecurrenceFrequence.WEEKLY)).isTrue();
        assertThat(TodoRecurrenceService.allowsEarlyCompletion(TodoRecurrenceFrequence.MONTHLY)).isTrue();
        assertThat(TodoRecurrenceService.allowsEarlyCompletion(TodoRecurrenceFrequence.DAILY)).isFalse();
        assertThat(TodoRecurrenceService.allowsEarlyCompletion(TodoRecurrenceFrequence.INTERVAL_DAYS)).isFalse();
    }

    @Test
    void recomputeProchaineEcheance_advancesPastNow() {
        stubClock("2026-09-16T10:00:00Z");
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(1)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 7)) // lundi passé
                .build();
        service.recomputeProchaineEcheance(rule);
        assertThat(rule.getProchaineEcheance()).isEqualTo(LocalDateTime.of(2026, 9, 21, 8, 0));
    }
}
