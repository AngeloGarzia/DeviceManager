package com.devicemanager.schedule;

import com.devicemanager.entity.Commande;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
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

/**
 * Relance quotidienne des commandes PENDING/SENT trop anciennes → {@code MAIL_ADMIN_EMAIL}.
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
    private final TransactionalMail transactionalMail;

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

        String recipient = transactionalMail.getAdminEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            log.warn("Relance commandes ignorée — MAIL_ADMIN_EMAIL invalide");
            return;
        }

        String periodKey = scheduledJobSupport.periodKeyToday();
        if (scheduledJobSupport.alreadySent(JOB_KEY, periodKey, recipient)) {
            return;
        }

        int minAgeDays = resolveMinAgeDays();
        LocalDateTime now = scheduledJobSupport.now();
        List<Commande> stale = orderRequestService.listStalePendingForReminder(minAgeDays, now);
        if (stale.isEmpty()) {
            log.debug("Relance commandes — aucune PENDING/SENT ≥ {} j (period={})", minAgeDays, periodKey);
            return;
        }

        List<OrderRequestRelanceEmail.OrderLine> lines = new ArrayList<>();
        for (Commande c : stale) {
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

        if (!scheduledJobSupport.tryClaim(JOB_KEY, periodKey, recipient)) {
            return;
        }
        RenderedEmail email = OrderRequestRelanceEmail.render(
                new OrderRequestRelanceEmail.Context(periodKey, minAgeDays, lines));
        EmailSendResult result = transactionalMail.notifyAdminOrderRelance(
                email.subject(), email.text(), email.html());
        if (result.ok()) {
            log.info("Relance commandes envoyée period={} lines={} simulated={}",
                    periodKey, lines.size(), result.skipped());
        } else {
            scheduledJobSupport.releaseClaim(JOB_KEY, periodKey, recipient);
            log.warn("Relance commandes échec period={} error={}", periodKey, result.error());
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
