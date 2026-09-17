package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.TodoService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoOverdueReminderTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private TodoService todoService;
    @Mock private TransactionalMail transactionalMail;
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
        lenient().when(transactionalMail.getAdminEmail()).thenReturn("admin@casino.local");
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
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
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_OVERDUE_HOUR,
                AppSettingsService.SCHED_TODO_OVERDUE_MINUTE,
                8, 0)).thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenAdminEmailInvalid() {
        when(transactionalMail.getAdminEmail()).thenReturn("nope");
        task.run();
        verify(todoService, never()).listOverdueForReminder(any());
    }

    @Test
    void run_emptyList_doesNotSendNorMark() {
        when(todoService.listOverdueForReminder(any())).thenReturn(List.of());
        task.run();
        verify(transactionalMail, never()).notifyAdminTodoOverdueReminder(any(), any(), any());
        verify(scheduledJobSupport, never()).markSent(anyString(), anyString(), anyString());
    }

    @Test
    void run_sendsDigestAndClaims() {
        when(todoService.listOverdueForReminder(any())).thenReturn(List.of(todo()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminTodoOverdueReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());
        task.run();
        verify(scheduledJobSupport).tryClaim(
                TodoOverdueReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    @Test
    void run_skipsWhenAlreadySent() {
        when(scheduledJobSupport.alreadySent(
                TodoOverdueReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local"))
                .thenReturn(true);
        task.run();
        verify(todoService, never()).listOverdueForReminder(any());
    }

    @Test
    void run_releasesClaimWhenSmtpFails() {
        when(todoService.listOverdueForReminder(any())).thenReturn(List.of(todo()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminTodoOverdueReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failure("SMTP down"));
        task.run();
        verify(scheduledJobSupport).releaseClaim(
                TodoOverdueReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    private static TodoTache todo() {
        return TodoTache.builder()
                .id(5L)
                .titre("Vérifier MAS")
                .statut(TodoTacheStatut.OPEN)
                .severite("HIGH")
                .dueAt(LocalDateTime.of(2026, 9, 10, 8, 0))
                .atelier(Atelier.builder().nom("Centre").utilise(true).build())
                .build();
    }
}
