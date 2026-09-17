package com.devicemanager.schedule;

import com.devicemanager.entity.ArretMaintenance;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.ArretMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArretMaintenanceReminderTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private ArretMaintenanceService arretMaintenanceService;
    @Mock private TransactionalMail transactionalMail;
    @InjectMocks private ArretMaintenanceReminderTask task;

    @BeforeEach
    void setUp() {
        lenient().when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(true);
        lenient().when(appSettingsService.getBoolean(AppSettingsService.SCHED_ARRET_MAINT_ENABLED, true))
                .thenReturn(true);
        lenient().when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ARRET_MAINT_HOUR,
                AppSettingsService.SCHED_ARRET_MAINT_MINUTE,
                8, 30)).thenReturn(true);
        lenient().when(transactionalMail.getAdminEmail()).thenReturn("admin@casino.local");
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        lenient().when(appSettingsService.getLong(AppSettingsService.SCHED_ARRET_MAINT_DAYS, 3)).thenReturn(3L);
        lenient().when(scheduledJobSupport.now()).thenReturn(LocalDateTime.of(2026, 9, 17, 8, 35));
    }

    @Test
    void run_skipsWhenGloballyDisabled() {
        when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(false);
        task.run();
        verify(arretMaintenanceService, never()).listStaleOpenForReminder(anyInt(), any());
    }

    @Test
    void run_skipsWhenJobDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_ARRET_MAINT_ENABLED, true))
                .thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ARRET_MAINT_HOUR,
                AppSettingsService.SCHED_ARRET_MAINT_MINUTE,
                8, 30)).thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenAdminEmailInvalid() {
        when(transactionalMail.getAdminEmail()).thenReturn(null);
        task.run();
        verify(arretMaintenanceService, never()).listStaleOpenForReminder(anyInt(), any());
    }

    @Test
    void run_emptyList_doesNotSendNorMark() {
        when(arretMaintenanceService.listStaleOpenForReminder(anyInt(), any())).thenReturn(List.of());
        task.run();
        verify(transactionalMail, never()).notifyAdminArretMaintenanceReminder(any(), any(), any());
        verify(scheduledJobSupport, never()).markSent(anyString(), anyString(), anyString());
    }

    @Test
    void run_sendsDigestAndClaims() {
        when(arretMaintenanceService.listStaleOpenForReminder(anyInt(), any())).thenReturn(List.of(arret()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminArretMaintenanceReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());
        task.run();
        verify(scheduledJobSupport).tryClaim(
                ArretMaintenanceReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    @Test
    void run_skipsWhenAlreadySent() {
        when(scheduledJobSupport.alreadySent(
                ArretMaintenanceReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local"))
                .thenReturn(true);
        task.run();
        verify(arretMaintenanceService, never()).listStaleOpenForReminder(anyInt(), any());
    }

    @Test
    void run_releasesClaimWhenSmtpFails() {
        when(arretMaintenanceService.listStaleOpenForReminder(anyInt(), any())).thenReturn(List.of(arret()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminArretMaintenanceReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failure("SMTP down"));
        task.run();
        verify(scheduledJobSupport).releaseClaim(
                ArretMaintenanceReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    private static ArretMaintenance arret() {
        return ArretMaintenance.builder()
                .id(9L)
                .atelier(Atelier.builder().nom("Atelier Centre").utilise(true).build())
                .mas(Mas.builder().numero("MAS-001").build())
                .dateHeureArret(LocalDateTime.of(2026, 9, 10, 9, 0))
                .motifArret("Carte mère")
                .adminUsername("admin")
                .build();
    }
}
