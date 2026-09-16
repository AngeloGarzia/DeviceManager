package com.devicemanager.service;

import com.devicemanager.entity.TodoRecurrence;
import com.devicemanager.entity.TodoRecurrenceFrequence;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoRecurrenceRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

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
}
