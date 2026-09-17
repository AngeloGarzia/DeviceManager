package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.Commande;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.OrderRequestService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRequestRelanceTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private OrderRequestService orderRequestService;
    @Mock private ScheduledDigestMailer digestMailer;
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
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
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
        verify(orderRequestService, never()).listStalePendingForReminder(anyInt(), any());
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ORDER_RELANC_HOUR,
                AppSettingsService.SCHED_ORDER_RELANC_MINUTE,
                8, 15)).thenReturn(false);
        task.run();
        verify(orderRequestService, never()).listStalePendingForReminder(anyInt(), any());
    }

    @Test
    void run_emptyList_doesNotSend() {
        when(orderRequestService.listStalePendingForReminder(anyInt(), any())).thenReturn(List.of());
        task.run();
        verify(digestMailer, never()).sendToCasinoAdmins(anyString(), anyString(), any(), any());
    }

    @Test
    void run_groupsByCasinoAndSendsDigest() {
        Casino casino = Casino.builder().id(1L).nom("Casino A").build();
        Atelier atelier = Atelier.builder().id(10L).nom("Atelier 1").casino(casino).build();
        Commande cmd = Commande.builder()
                .id(100L)
                .atelier(atelier)
                .status("PENDING")
                .technicienNom("Jean")
                .dateDemande(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        when(orderRequestService.listStalePendingForReminder(eq(7), any())).thenReturn(List.of(cmd));

        task.run();

        ArgumentCaptor<Casino> casinoCap = ArgumentCaptor.forClass(Casino.class);
        ArgumentCaptor<RenderedEmail> emailCap = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(digestMailer).sendToCasinoAdmins(
                eq(OrderRequestRelanceTask.JOB_KEY),
                eq("2026-09-17"),
                casinoCap.capture(),
                emailCap.capture());
        assertThat(casinoCap.getValue().getId()).isEqualTo(1L);
        assertThat(emailCap.getValue().subject()).isNotBlank();
    }
}
