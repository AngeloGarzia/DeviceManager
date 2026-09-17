package com.devicemanager.schedule;

import com.devicemanager.entity.ArretMaintenance;
import com.devicemanager.entity.Casino;
import com.devicemanager.mail.RenderedEmail;
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
import java.util.Map;

/**
 * Rappel quotidien des arrêts maintenance ouverts trop longtemps
 * → ADMIN / SUPER_ADMIN du casino concerné.
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
    private final ScheduledDigestMailer digestMailer;

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

        int minAgeDays = resolveMinAgeDays();
        LocalDateTime now = scheduledJobSupport.now();
        List<ArretMaintenance> stale = arretMaintenanceService.listStaleOpenForReminder(minAgeDays, now);
        if (stale.isEmpty()) {
            log.debug("Rappel arrêts — aucun ouvert ≥ {} j", minAgeDays);
            return;
        }

        String dateKey = scheduledJobSupport.periodKeyToday();
        Map<Casino, List<ArretMaintenance>> byCasino =
                ScheduledDigestMailer.groupByCasino(stale, ArretMaintenance::getAtelier);
        for (Map.Entry<Casino, List<ArretMaintenance>> entry : byCasino.entrySet()) {
            List<ArretMaintenanceReminderEmail.ArretLine> lines = new ArrayList<>();
            for (ArretMaintenance a : entry.getValue()) {
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
            RenderedEmail email = ArretMaintenanceReminderEmail.render(
                    new ArretMaintenanceReminderEmail.Context(dateKey, minAgeDays, lines));
            digestMailer.sendToCasinoAdmins(JOB_KEY, dateKey, entry.getKey(), email);
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
