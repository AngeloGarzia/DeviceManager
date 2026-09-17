package com.devicemanager.schedule;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.VisiteQuadriService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class VisiteQuadriReminderTaskTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private AppSettingsService appSettingsService;
    @Mock private AtelierRepository atelierRepository;
    @Mock private VisiteQuadriService visiteQuadriService;
    @Mock private ScheduledDigestMailer digestMailer;
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
        lenient().when(scheduledJobSupport.periodKeyToday()).thenReturn("2026-09-17");
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
        verify(atelierRepository, never()).findAllActiveWithCasino();
    }

    @Test
    void run_skipsWhenNotDueYet() {
        when(scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_VISITE_QUADRI_HOUR,
                AppSettingsService.SCHED_VISITE_QUADRI_MINUTE,
                8, 0)).thenReturn(false);
        task.run();
        verify(atelierRepository, never()).findAllActiveWithCasino();
    }

    @Test
    void run_emptyAlerts_doesNotSend() {
        Casino casino = Casino.builder().id(1L).nom("A").build();
        when(atelierRepository.findAllActiveWithCasino()).thenReturn(List.of(
                Atelier.builder().id(100L).nom("A").utilise(true).casino(casino).build()));
        when(visiteQuadriService.listAlertObligationsForAtelier(any(), anyInt(), any()))
                .thenReturn(List.of());
        task.run();
        verify(digestMailer, never()).sendToCasinoAdmins(anyString(), anyString(), any(), any());
    }

    @Test
    void run_groupsByCasinoAndSendsDigest() {
        Casino casino = Casino.builder().id(1L).nom("Casino A").build();
        when(atelierRepository.findAllActiveWithCasino()).thenReturn(List.of(
                Atelier.builder().id(100L).nom("Atelier Centre").utilise(true).casino(casino).build()));
        when(visiteQuadriService.listAlertObligationsForAtelier(eq(100L), eq(7), any()))
                .thenReturn(List.of(alert()));

        task.run();

        ArgumentCaptor<Casino> casinoCap = ArgumentCaptor.forClass(Casino.class);
        verify(digestMailer).sendToCasinoAdmins(
                eq(VisiteQuadriReminderTask.JOB_KEY),
                eq("2026-09-17"),
                casinoCap.capture(),
                any(RenderedEmail.class));
        assertThat(casinoCap.getValue().getId()).isEqualTo(1L);
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
