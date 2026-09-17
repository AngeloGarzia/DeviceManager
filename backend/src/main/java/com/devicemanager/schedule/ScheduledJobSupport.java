package com.devicemanager.schedule;

import com.devicemanager.entity.ScheduledMailSend;
import com.devicemanager.repository.ScheduledMailSendRepository;
import com.devicemanager.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Helpers partagés des jobs métier planifiés : fuseau, fenêtre horaire, idempotence mail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobSupport {

    private final AppSettingsService appSettingsService;
    private final ScheduledMailSendRepository scheduledMailSendRepository;
    private final Clock clock;

    public boolean isSchedulingGloballyEnabled() {
        return appSettingsService.getBoolean(AppSettingsService.SCHED_ENABLED, true);
    }

    public ZoneId zoneId() {
        String raw = appSettingsService.get(AppSettingsService.SCHED_TIMEZONE, "Europe/Paris");
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException ex) {
            log.warn("SCHED_TIMEZONE invalide « {} », repli Europe/Paris", raw);
            return ZoneId.of("Europe/Paris");
        }
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(zoneId()));
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    /**
     * True si l'heure locale a atteint (ou dépassé) l'horaire configuré pour aujourd'hui.
     * Permet les retries SMTP le même jour après un échec.
     */
    public boolean isDueToday(String hourKey, String minuteKey, int defaultHour, int defaultMinute) {
        int hour = clamp((int) appSettingsService.getLong(hourKey, defaultHour), 0, 23);
        int minute = clamp((int) appSettingsService.getLong(minuteKey, defaultMinute), 0, 59);
        LocalDateTime now = now();
        LocalDateTime scheduled = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute));
        return !now.isBefore(scheduled);
    }

    public boolean alreadySent(String jobKey, String periodKey, String recipient) {
        return scheduledMailSendRepository.existsByJobKeyAndPeriodKeyAndRecipient(
                jobKey, periodKey, normalizeRecipient(recipient));
    }

    /**
     * Enregistre un envoi réussi. Concurrent-safe via contrainte unique.
     *
     * @return true si cette instance a enregistré l'envoi (false si déjà présent)
     */
    public boolean markSent(String jobKey, String periodKey, String recipient) {
        String normalized = normalizeRecipient(recipient);
        if (alreadySent(jobKey, periodKey, normalized)) {
            return false;
        }
        try {
            scheduledMailSendRepository.save(ScheduledMailSend.builder()
                    .jobKey(jobKey)
                    .periodKey(periodKey)
                    .recipient(normalized)
                    .sentAt(now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Envoi déjà tracé (course) job={} period={}", jobKey, periodKey);
            return false;
        }
    }

    /**
     * Réserve la période avant l'envoi SMTP (anti double-envoi multi-instance).
     * En cas d'échec SMTP, appeler {@link #releaseClaim}.
     */
    public boolean tryClaim(String jobKey, String periodKey, String recipient) {
        return markSent(jobKey, periodKey, recipient);
    }

    public void releaseClaim(String jobKey, String periodKey, String recipient) {
        scheduledMailSendRepository.deleteByJobKeyAndPeriodKeyAndRecipient(
                jobKey, periodKey, normalizeRecipient(recipient));
    }

    private static String normalizeRecipient(String recipient) {
        return recipient == null ? "" : recipient.trim().toLowerCase();
    }

    public String periodKeyToday() {
        return today().toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
