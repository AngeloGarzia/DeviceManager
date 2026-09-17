package com.devicemanager.schedule;

import com.devicemanager.entity.ScheduledMailSend;
import com.devicemanager.repository.ScheduledMailSendRepository;
import com.devicemanager.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledJobSupportTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Mock private AppSettingsService appSettingsService;
    @Mock private ScheduledMailSendRepository scheduledMailSendRepository;

    private ScheduledJobSupport support;

    @BeforeEach
    void setUp() {
        // 2026-09-17 07:59 Europe/Paris
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T05:59:00Z"), PARIS);
        support = new ScheduledJobSupport(appSettingsService, scheduledMailSendRepository, clock);
        lenient().when(appSettingsService.get(AppSettingsService.SCHED_TIMEZONE, "Europe/Paris"))
                .thenReturn("Europe/Paris");
    }

    @Test
    void zoneId_fallsBackWhenInvalid() {
        when(appSettingsService.get(AppSettingsService.SCHED_TIMEZONE, "Europe/Paris"))
                .thenReturn("Not/AZone");
        assertThat(support.zoneId()).isEqualTo(PARIS);
    }

    @Test
    void isDueToday_falseBeforeScheduledTime() {
        when(appSettingsService.getLong("H", 8)).thenReturn(8L);
        when(appSettingsService.getLong("M", 0)).thenReturn(0L);
        assertThat(support.isDueToday("H", "M", 8, 0)).isFalse();
    }

    @Test
    void isDueToday_trueAtOrAfterScheduledTime() {
        Clock atEight = Clock.fixed(Instant.parse("2026-09-17T06:00:00Z"), PARIS);
        support = new ScheduledJobSupport(appSettingsService, scheduledMailSendRepository, atEight);
        when(appSettingsService.getLong("H", 8)).thenReturn(8L);
        when(appSettingsService.getLong("M", 0)).thenReturn(0L);
        assertThat(support.isDueToday("H", "M", 8, 0)).isTrue();
    }

    @Test
    void isDueToday_clampsHourAndMinute() {
        when(appSettingsService.getLong("H", 8)).thenReturn(99L);
        when(appSettingsService.getLong("M", 0)).thenReturn(-5L);
        // clamped to 23:00 — 07:59 is before → false
        assertThat(support.isDueToday("H", "M", 8, 0)).isFalse();
    }

    @Test
    void markSent_normalizesRecipient() {
        when(scheduledMailSendRepository.existsByJobKeyAndPeriodKeyAndRecipient(
                "VISITE_QUADRI", "2026-09-17", "admin@casino.local")).thenReturn(false);
        when(scheduledMailSendRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean marked = support.markSent("VISITE_QUADRI", "2026-09-17", "  Admin@Casino.Local ");

        assertThat(marked).isTrue();
        ArgumentCaptor<ScheduledMailSend> captor = ArgumentCaptor.forClass(ScheduledMailSend.class);
        verify(scheduledMailSendRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("admin@casino.local");
        assertThat(captor.getValue().getSentAt()).isEqualTo(LocalDateTime.of(2026, 9, 17, 7, 59));
    }

    @Test
    void markSent_returnsFalseWhenAlreadyExists() {
        when(scheduledMailSendRepository.existsByJobKeyAndPeriodKeyAndRecipient(
                eq("JOB"), eq("2026-09-17"), eq("a@b.c"))).thenReturn(true);

        assertThat(support.markSent("JOB", "2026-09-17", "a@b.c")).isFalse();
        verify(scheduledMailSendRepository, never()).save(any());
    }

    @Test
    void markSent_returnsFalseOnRace() {
        when(scheduledMailSendRepository.existsByJobKeyAndPeriodKeyAndRecipient(
                any(), any(), any())).thenReturn(false);
        when(scheduledMailSendRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(support.markSent("JOB", "2026-09-17", "a@b.c")).isFalse();
    }

    @Test
    void periodKeyToday_usesZone() {
        assertThat(support.periodKeyToday()).isEqualTo("2026-09-17");
    }

    @Test
    void isSchedulingGloballyEnabled_readsSetting() {
        when(appSettingsService.getBoolean(AppSettingsService.SCHED_ENABLED, true)).thenReturn(false);
        assertThat(support.isSchedulingGloballyEnabled()).isFalse();
    }
}
