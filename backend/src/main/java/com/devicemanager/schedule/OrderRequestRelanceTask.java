package com.devicemanager.schedule;

import com.devicemanager.entity.Casino;
import com.devicemanager.entity.Commande;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.templates.OrderRequestRelanceEmail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.OrderRequestService;
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
 * Relance quotidienne des commandes PENDING/SENT trop anciennes
 * → ADMIN / SUPER_ADMIN du casino concerné.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderRequestRelanceTask {

    public static final String JOB_KEY = "ORDER_RELANC";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ScheduledJobSupport scheduledJobSupport;
    private final AppSettingsService appSettingsService;
    private final OrderRequestService orderRequestService;
    private final ScheduledDigestMailer digestMailer;

    @Scheduled(cron = "${app.schedule.tick-cron:0 * * * * *}")
    @Transactional
    public void run() {
        if (!scheduledJobSupport.isSchedulingGloballyEnabled()) {
            return;
        }
        if (!appSettingsService.getBoolean(AppSettingsService.SCHED_ORDER_RELANC_ENABLED, true)) {
            return;
        }
        if (!scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_ORDER_RELANC_HOUR,
                AppSettingsService.SCHED_ORDER_RELANC_MINUTE,
                8, 15)) {
            return;
        }

        int minAgeDays = resolveMinAgeDays();
        LocalDateTime now = scheduledJobSupport.now();
        List<Commande> stale = orderRequestService.listStalePendingForReminder(minAgeDays, now);
        if (stale.isEmpty()) {
            log.debug("Relance commandes — aucune PENDING/SENT ≥ {} j", minAgeDays);
            return;
        }

        String dateKey = scheduledJobSupport.periodKeyToday();
        Map<Casino, List<Commande>> byCasino = ScheduledDigestMailer.groupByCasino(stale, Commande::getAtelier);
        for (Map.Entry<Casino, List<Commande>> entry : byCasino.entrySet()) {
            Casino casino = entry.getKey();
            List<OrderRequestRelanceEmail.OrderLine> lines = new ArrayList<>();
            for (Commande c : entry.getValue()) {
                long ageDays = c.getDateDemande() == null
                        ? 0
                        : Math.max(0, ChronoUnit.DAYS.between(c.getDateDemande().toLocalDate(), now.toLocalDate()));
                int lineCount = c.getLignes() == null ? 0 : c.getLignes().size();
                lines.add(new OrderRequestRelanceEmail.OrderLine(
                        c.getId(),
                        c.getAtelier() != null ? c.getAtelier().getNom() : null,
                        c.getTechnicienNom(),
                        c.getStatus(),
                        c.getDateDemande() == null ? null : c.getDateDemande().format(DATE_FMT),
                        ageDays,
                        lineCount));
            }
            RenderedEmail email = OrderRequestRelanceEmail.render(
                    new OrderRequestRelanceEmail.Context(dateKey, minAgeDays, lines));
            digestMailer.sendToCasinoAdmins(JOB_KEY, dateKey, casino, email);
        }
    }

    private int resolveMinAgeDays() {
        long days = appSettingsService.getLong(AppSettingsService.SCHED_ORDER_RELANC_DAYS, 7);
        if (days < 1) {
            return 7;
        }
        return (int) Math.min(days, 365);
    }
}
