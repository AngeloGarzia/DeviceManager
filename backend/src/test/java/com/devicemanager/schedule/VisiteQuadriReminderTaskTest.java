package com.devicemanager.schedule;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.VisiteQuadriService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisiteQuadriReminderTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private AtelierRepository atelierRepository;
    @Mock private VisiteQuadriService visiteQuadriService;
    @Mock private TransactionalMail transactionalMail;
    @InjectMocks private VisiteQuadriReminderTask task;

    @BeforeEach
    void setUp() {
        lenient().when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(true);
        lenient().when(appSettingsService.getBoolean(AppSettingsService.SCHED_VISITE_QUADRI_ENABLED, true))
                .thenReturn(true);
        lenient().when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_VISITE_QUADRI_HOUR,
                AppSettingsService.SCHED_VISITE_QUADRI_MINUTE,
                8, 0)).thenReturn(true);
        lenient().when(transactionalMail.getAdminEmail()).thenReturn("admin@casino.local");
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        lenient().when(visiteQuadriService.resolveWarnDays()).thenReturn(7);
        lenient().when(scheduledJobSupport.today()).thenReturn(LocalDate.of(2026, 9, 17));
    }

    @Test
    void run_skipsWhenGloballyDisabled() {
        when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(false);
        task.run();
        verify(appSettingsService, never()).getBoolean(anyString(), any(Boolean.class));
    }

    @Test
    void run_skipsWhenJobDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_VISITE_QUADRI_ENABLED, true))
                .thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_VISITE_QUADRI_HOUR,
                AppSettingsService.SCHED_VISITE_QUADRI_MINUTE,
                8, 0)).thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenAdminEmailInvalid() {
        when(transactionalMail.getAdminEmail()).thenReturn("not-an-email");
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_emptyAlerts_doesNotSendNorMark() {
        when(atelierRepository.findAll()).thenReturn(List.of(
                Atelier.builder().id(100L).nom("A").utilise(true).build()));
        when(visiteQuadriService.listAlertObligationsForAtelier(any(), anyInt(), any()))
                .thenReturn(List.of());
        task.run();
        verify(transactionalMail, never()).notifyAdminVisiteQuadriReminder(any(), any(), any());
        verify(scheduledJobSupport, never()).markSent(anyString(), anyString(), anyString());
    }

    @Test
    void run_ignoresUnusedAteliers() {
        Atelier unused = Atelier.builder().id(99L).nom("Off").utilise(false).build();
        Atelier used = Atelier.builder().id(100L).nom("On").utilise(true).build();
        when(atelierRepository.findAll()).thenReturn(List.of(unused, used));
        when(visiteQuadriService.listAlertObligationsForAtelier(eq(100L), eq(7), any()))
                .thenReturn(List.of(alert()));
        when(transactionalMail.notifyAdminVisiteQuadriReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);

        task.run();

        verify(transactionalMail).notifyAdminVisiteQuadriReminder(anyString(), anyString(), anyString());
        verify(scheduledJobSupport).tryClaim(
                VisiteQuadriReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
        verify(visiteQuadriService, never()).listAlertObligationsForAtelier(eq(99L), anyInt(), any());
    }

    @Test
    void run_sendsDigestAndClaims() {
        when(atelierRepository.findAll()).thenReturn(List.of(
                Atelier.builder().id(100L).nom("Atelier Centre").utilise(true).build()));
        when(visiteQuadriService.listAlertObligationsForAtelier(eq(100L), eq(7), any()))
                .thenReturn(List.of(alert()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminVisiteQuadriReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());

        task.run();

        verify(transactionalMail).notifyAdminVisiteQuadriReminder(anyString(), anyString(), anyString());
        verify(scheduledJobSupport).tryClaim(
                VisiteQuadriReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
        verify(scheduledJobSupport, never()).releaseClaim(anyString(), anyString(), anyString());
    }

    @Test
    void run_skipsWhenAlreadySent() {
        when(scheduledJobSupport.alreadySent(
                VisiteQuadriReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local"))
                .thenReturn(true);
        task.run();
        verify(atelierRepository, never()).findAll();
    }

    @Test
    void run_releasesClaimWhenSmtpFails() {
        when(atelierRepository.findAll()).thenReturn(List.of(
                Atelier.builder().id(100L).nom("A").utilise(true).build()));
        when(visiteQuadriService.listAlertObligationsForAtelier(any(), anyInt(), any()))
                .thenReturn(List.of(alert()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminVisiteQuadriReminder(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failure("SMTP down"));

        task.run();

        verify(scheduledJobSupport).releaseClaim(
                VisiteQuadriReminderTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    private static VisiteQuadriObligationResponse alert() {
        return VisiteQuadriObligationResponse.builder()
                .sfmId(1L)
                .sfmNom("SFM A")
                .marqueId(10L)
                .marqueLabel("Novomatic")
                .dueDate(LocalDate.of(2026, 9, 10))
                .daysRemaining(-7L)
                .level(VisiteQuadriService.LEVEL_OVERDUE)
                .build();
    }
}
