package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.TodoRecurrenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Matérialise les occurrences de todos récurrents dues (tous ateliers actifs).
 * Pas d'e-mail — idempotence journalière via {@link ScheduledJobSupport}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoRecurrenceMaterializationTask {

    public static final String JOB_KEY = "TODO_RECURRENCE";
    /** Destinataire fictif pour l'idempotence (pas d'envoi mail). */
    static final String IDEMPOTENCE_RECIPIENT = "system@local";

    private final ScheduledJobSupport scheduledJobSupport;
    private final AppSettingsService appSettingsService;
    private final AtelierRepository atelierRepository;
    private final TodoRecurrenceService todoRecurrenceService;

    @Scheduled(cron = "${app.schedule.tick-cron:0 * * * * *}")
    @Transactional
    public void run() {
        if (!scheduledJobSupport.isSchedulingGloballyEnabled()) {
            return;
        }
        if (!appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_RECURRENCE_ENABLED, true)) {
            return;
        }
        if (!scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_RECURRENCE_HOUR,
                AppSettingsService.SCHED_TODO_RECURRENCE_MINUTE,
                6, 30)) {
            return;
        }

        String periodKey = scheduledJobSupport.periodKeyToday();
        if (scheduledJobSupport.alreadySent(JOB_KEY, periodKey, IDEMPOTENCE_RECIPIENT)) {
            return;
        }

        LocalDateTime now = scheduledJobSupport.now();
        int ateliers = 0;
        int failures = 0;
        for (Atelier atelier : atelierRepository.findAll()) {
            if (!atelier.isUtilise()) {
                continue;
            }
            try {
                todoRecurrenceService.generateDueOccurrences(atelier, now);
                ateliers++;
            } catch (Exception ex) {
                failures++;
                log.warn("Matérialisation todos récurrents échouée atelier={}: {}",
                        atelier.getId(), ex.getMessage());
            }
        }
        if (failures > 0) {
            log.warn("Matérialisation todos récurrents incomplete period={} ok={} fail={} — pas d'idempotence",
                    periodKey, ateliers, failures);
            return;
        }
        scheduledJobSupport.markSent(JOB_KEY, periodKey, IDEMPOTENCE_RECIPIENT);
        log.info("Matérialisation todos récurrents terminée period={} ateliers={}", periodKey, ateliers);
    }
}
