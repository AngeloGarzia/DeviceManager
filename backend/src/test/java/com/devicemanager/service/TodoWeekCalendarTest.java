package com.devicemanager.service;

import com.devicemanager.entity.TodoRecurrence;
import com.devicemanager.entity.TodoRecurrenceFrequence;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoRecurrenceRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TodoWeekCalendarTest {

    @Mock private TodoRecurrenceRepository todoRecurrenceRepository;
    @Mock private TodoTacheRepository todoTacheRepository;
    @Mock private MasRepository masRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @Mock private Clock clock;
    @InjectMocks private TodoRecurrenceService service;

    @Test
    void isDueOn_weeklyOnlyOnMatchingDay() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.WEEKLY)
                .jourSemaine(3) // mercredi
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 1))
                .active(true)
                .build();

        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 16))).isTrue(); // mercredi
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 15))).isFalse(); // mardi
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 2))).isTrue(); // mercredi
    }

    @Test
    void isDueOn_intervalDays() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.INTERVAL_DAYS)
                .intervalDays(3)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 14))
                .active(true)
                .build();

        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 14))).isTrue();
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 17))).isTrue();
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 15))).isFalse();
    }

    @Test
    void isDueOn_respectsDateFin() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.DAILY)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 9, 14))
                .dateFin(LocalDate.of(2026, 9, 16))
                .active(true)
                .build();

        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 16))).isTrue();
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 17))).isFalse();
    }

    @Test
    void isDueOn_monthly() {
        TodoRecurrence rule = TodoRecurrence.builder()
                .frequence(TodoRecurrenceFrequence.MONTHLY)
                .jourMois(15)
                .heureDue(LocalTime.of(8, 0))
                .dateDebut(LocalDate.of(2026, 8, 1))
                .active(true)
                .build();

        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 15))).isTrue();
        assertThat(service.isDueOn(rule, LocalDate.of(2026, 9, 14))).isFalse();
    }
}
