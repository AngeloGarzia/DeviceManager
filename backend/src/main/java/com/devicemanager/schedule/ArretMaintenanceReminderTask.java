package com.devicemanager.schedule;

import com.devicemanager.entity.ArretMaintenance;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.mail.templates.ArretMaintenanceReminderEmail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.ArretMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappel quotidien des arrêts maintenance ouverts trop longtemps → {@code MAIL_ADMIN_EMAIL}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArretMaintenanceReminderTask {

    public static final String JOB_KEY = "ARRET_MAINT";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ScheduledJobSupport scheduledJobSupport;
    private final AppSettingsService appSettingsService;
    private final ArretMaintenanceService arretMaintenanceService;
    private final TransactionalMail transactionalMail;

    @Scheduled(cron = "${app.schedule.tick-cron:0 * * * * *}")
    @Transactional
    public void run() {
        if (!scheduledJobSupport.isSchedulingGloballyEnabled()) {
            return;
        }
        if (!appSettingsService.getBoolean(AppSettingsService.SCHED_ARRET_MAINT_ENABLED, true)) {
            return;
        }
        if (!scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ARRET_MAINT_HOUR,
                AppSettingsService.SCHED_ARRET_MAINT_MINUTE,
                8, 30)) {
            return;
        }

        String recipient = transactionalMail.getAdminEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            log.warn("Rappel arrêts maintenance ignoré — MAIL_ADMIN_EMAIL invalide");
            return;
        }

        String periodKey = scheduledJobSupport.periodKeyToday();
        if (scheduledJobSupport.alreadySent(JOB_KEY, periodKey, recipient)) {
            return;
        }

        int minAgeDays = resolveMinAgeDays();
        LocalDateTime now = scheduledJobSupport.now();
        List<ArretMaintenance> stale = arretMaintenanceService.listStaleOpenForReminder(minAgeDays, now);
        if (stale.isEmpty()) {
            log.debug("Rappel arrêts — aucun ouvert ≥ {} j (period={})", minAgeDays, periodKey);
            return;
        }

        List<ArretMaintenanceReminderEmail.ArretLine> lines = new ArrayList<>();
        for (ArretMaintenance a : stale) {
            long ageDays = a.getDateHeureArret() == null
                    ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(a.getDateHeureArret().toLocalDate(), now.toLocalDate()));
            lines.add(new ArretMaintenanceReminderEmail.ArretLine(
                    a.getId(),
                    a.getAtelier() != null ? a.getAtelier().getNom() : null,
                    a.getMas() != null ? a.getMas().getNumero() : null,
                    a.getMotifArret(),
                    a.getDateHeureArret() == null ? null : a.getDateHeureArret().format(DATE_FMT),
                    ageDays));
        }

        if (!scheduledJobSupport.tryClaim(JOB_KEY, periodKey, recipient)) {
            return;
        }
        RenderedEmail email = ArretMaintenanceReminderEmail.render(
                new ArretMaintenanceReminderEmail.Context(periodKey, minAgeDays, lines));
        EmailSendResult result = transactionalMail.notifyAdminArretMaintenanceReminder(
                email.subject(), email.text(), email.html());
        if (result.ok()) {
            log.info("Rappel arrêts maintenance envoyé period={} lines={} simulated={}",
                    periodKey, lines.size(), result.skipped());
        } else {
            scheduledJobSupport.releaseClaim(JOB_KEY, periodKey, recipient);
            log.warn("Rappel arrêts maintenance échec period={} error={}", periodKey, result.error());
        }
    }

    private int resolveMinAgeDays() {
        long days = appSettingsService.getLong(AppSettingsService.SCHED_ARRET_MAINT_DAYS, 3);
        if (days < 1) {
            return 3;
        }
        return (int) Math.min(days, 365);
    }
}
