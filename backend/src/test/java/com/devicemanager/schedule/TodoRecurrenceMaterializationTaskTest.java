package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.TodoRecurrenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoRecurrenceMaterializationTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private AtelierRepository atelierRepository;
    @Mock private TodoRecurrenceService todoRecurrenceService;
    @InjectMocks private TodoRecurrenceMaterializationTask task;

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 17, 6, 35);

    @BeforeEach
    void setUp() {
        lenient().when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(true);
        lenient().when(appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_RECURRENCE_ENABLED, true))
                .thenReturn(true);
        lenient().when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_RECURRENCE_HOUR,
                AppSettingsService.SCHED_TODO_RECURRENCE_MINUTE,
                6, 30)).thenReturn(true);
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        lenient().when(scheduledJobSupport.now()).thenReturn(now);
    }

    @Test
    void run_skipsWhenGloballyDisabled() {
        when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(false);
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_skipsWhenJobDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_RECURRENCE_ENABLED, true))
                .thenReturn(false);
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_RECURRENCE_HOUR,
                AppSettingsService.SCHED_TODO_RECURRENCE_MINUTE,
                6, 30)).thenReturn(false);
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_materializesAllActiveAteliersAndMarks() {
        Atelier a1 = Atelier.builder().id(1L).nom("A").utilise(true).build();
        Atelier a2 = Atelier.builder().id(2L).nom("B").utilise(false).build();
        when(atelierRepository.findAll()).thenReturn(List.of(a1, a2));

        task.run();

        verify(todoRecurrenceService).generateDueOccurrences(a1, now);
        verify(todoRecurrenceService, never()).generateDueOccurrences(eq(a2), any());
        verify(scheduledJobSupport).markSent(
                TodoRecurrenceMaterializationTask.JOB_KEY,
                "2026-09-17",
                TodoRecurrenceMaterializationTask.IDEMPOTENCE_RECIPIENT);
    }

    @Test
    void run_skipsWhenAlreadyDone() {
        when(scheduledJobSupport.alreadySent(
                TodoRecurrenceMaterializationTask.JOB_KEY,
                "2026-09-17",
                TodoRecurrenceMaterializationTask.IDEMPOTENCE_RECIPIENT)).thenReturn(true);
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_continuesWhenOneAtelierFails_withoutMarking() {
        Atelier a1 = Atelier.builder().id(1L).nom("A").utilise(true).build();
        Atelier a2 = Atelier.builder().id(2L).nom("B").utilise(true).build();
        when(atelierRepository.findAll()).thenReturn(List.of(a1, a2));
        doThrow(new RuntimeException("boom"))
                .when(todoRecurrenceService).generateDueOccurrences(a1, now);

        task.run();

        verify(todoRecurrenceService).generateDueOccurrences(a2, now);
        verify(scheduledJobSupport, never()).markSent(anyString(), anyString(), anyString());
    }
}
