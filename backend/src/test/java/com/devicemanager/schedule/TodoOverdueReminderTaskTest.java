package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoOverdueReminderTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private TodoService todoService;
    @Mock private ScheduledDigestMailer digestMailer;
    @InjectMocks private TodoOverdueReminderTask task;

    @BeforeEach
    void setUp() {
        lenient().when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(true);
        lenient().when(appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_OVERDUE_ENABLED, true))
                .thenReturn(true);
        lenient().when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_OVERDUE_HOUR,
                AppSettingsService.SCHED_TODO_OVERDUE_MINUTE,
                8, 0)).thenReturn(true);
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.now()).thenReturn(LocalDateTime.of(2026, 9, 17, 8, 5));
    }

    @Test
    void run_skipsWhenGloballyDisabled() {
        when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(false);
        task.run();
        verify(todoService, never()).listOverdueForReminder(any());
    }

    @Test
    void run_skipsWhenJobDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_OVERDUE_ENABLED, true))
                .thenReturn(false);
        task.run();
        verify(todoService, never()).listOverdueForReminder(any());
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_OVERDUE_HOUR,
                AppSettingsService.SCHED_TODO_OVERDUE_MINUTE,
                8, 0)).thenReturn(false);
        task.run();
        verify(todoService, never()).listOverdueForReminder(any());
    }

    @Test
    void run_emptyList_doesNotSend() {
        when(todoService.listOverdueForReminder(any())).thenReturn(List.of());
        task.run();
        verify(digestMailer, never()).sendToCasinoAdmins(anyString(), anyString(), any(), any());
    }

    @Test
    void run_groupsByCasinoAndSendsDigest() {
        Casino casino = Casino.builder().id(3L).nom("Casino C").build();
        when(todoService.listOverdueForReminder(any())).thenReturn(List.of(
                TodoTache.builder()
                        .id(5L)
                        .titre("Vérifier MAS")
                        .statut(TodoTacheStatut.OPEN)
                        .severite("HIGH")
                        .dueAt(LocalDateTime.of(2026, 9, 10, 8, 0))
                        .atelier(Atelier.builder().nom("Centre").casino(casino).utilise(true).build())
                        .build()));

        task.run();

        ArgumentCaptor<Casino> casinoCap = ArgumentCaptor.forClass(Casino.class);
        verify(digestMailer).sendToCasinoAdmins(
                eq(TodoOverdueReminderTask.JOB_KEY),
                eq("2026-09-17"),
                casinoCap.capture(),
                any(RenderedEmail.class));
        assertThat(casinoCap.getValue().getId()).isEqualTo(3L);
    }
}
