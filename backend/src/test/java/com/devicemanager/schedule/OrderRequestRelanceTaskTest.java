package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.User;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.OrderRequestService;
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
class OrderRequestRelanceTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private OrderRequestService orderRequestService;
    @Mock private TransactionalMail transactionalMail;
    @InjectMocks private OrderRequestRelanceTask task;

    @BeforeEach
    void setUp() {
        lenient().when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(true);
        lenient().when(appSettingsService.getBoolean(AppSettingsService.SCHED_ORDER_RELANC_ENABLED, true))
                .thenReturn(true);
        lenient().when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ORDER_RELANC_HOUR,
                AppSettingsService.SCHED_ORDER_RELANC_MINUTE,
                8, 15)).thenReturn(true);
        lenient().when(transactionalMail.getAdminEmail()).thenReturn("admin@casino.local");
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
        lenient().when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        lenient().when(appSettingsService.getLong(AppSettingsService.SCHED_ORDER_RELANC_DAYS, 7)).thenReturn(7L);
        lenient().when(scheduledJobSupport.now()).thenReturn(LocalDateTime.of(2026, 9, 17, 8, 20));
    }

    @Test
    void run_skipsWhenGloballyDisabled() {
        when(scheduledJobSupport.isSchedulingGloballyEnabled()).thenReturn(false);
        task.run();
        verify(orderRequestService, never()).listStalePendingForReminder(anyInt(), any());
    }

    @Test
    void run_skipsWhenJobDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_ORDER_RELANC_ENABLED, true))
                .thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ORDER_RELANC_HOUR,
                AppSettingsService.SCHED_ORDER_RELANC_MINUTE,
                8, 15)).thenReturn(false);
        task.run();
        verify(transactionalMail, never()).getAdminEmail();
    }

    @Test
    void run_skipsWhenAdminEmailBlank() {
        when(transactionalMail.getAdminEmail()).thenReturn("  ");
        task.run();
        verify(orderRequestService, never()).listStalePendingForReminder(anyInt(), any());
    }

    @Test
    void run_emptyList_doesNotSendNorMark() {
        when(orderRequestService.listStalePendingForReminder(anyInt(), any())).thenReturn(List.of());
        task.run();
        verify(transactionalMail, never()).notifyAdminOrderRelance(any(), any(), any());
        verify(scheduledJobSupport, never()).markSent(anyString(), anyString(), anyString());
    }

    @Test
    void run_sendsDigestAndClaims() {
        when(orderRequestService.listStalePendingForReminder(anyInt(), any())).thenReturn(List.of(commande()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminOrderRelance(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());

        task.run();

        verify(transactionalMail).notifyAdminOrderRelance(anyString(), anyString(), anyString());
        verify(scheduledJobSupport).tryClaim(
                OrderRequestRelanceTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    @Test
    void run_skipsWhenAlreadySent() {
        when(scheduledJobSupport.alreadySent(
                OrderRequestRelanceTask.JOB_KEY, "2026-09-17", "admin@casino.local"))
                .thenReturn(true);
        task.run();
        verify(orderRequestService, never()).listStalePendingForReminder(anyInt(), any());
    }

    @Test
    void run_releasesClaimWhenSmtpFails() {
        when(orderRequestService.listStalePendingForReminder(anyInt(), any())).thenReturn(List.of(commande()));
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.notifyAdminOrderRelance(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failure("SMTP down"));
        task.run();
        verify(scheduledJobSupport).releaseClaim(
                OrderRequestRelanceTask.JOB_KEY, "2026-09-17", "admin@casino.local");
    }

    private static Commande commande() {
        return Commande.builder()
                .id(42L)
                .atelier(Atelier.builder().id(100L).nom("Atelier Centre").utilise(true).build())
                .technicien(User.builder().id(1L).username("tech").build())
                .technicienNom("Tech Un")
                .status("PENDING")
                .dateDemande(LocalDateTime.of(2026, 9, 1, 10, 0))
                .message("urgent")
                .build();
    }
}
