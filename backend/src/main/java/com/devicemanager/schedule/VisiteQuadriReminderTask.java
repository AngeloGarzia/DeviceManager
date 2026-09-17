package com.devicemanager.schedule;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.mail.templates.VisiteQuadriReminderEmail;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.VisiteQuadriService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappel quotidien des visites quadritrimestrielles WARN / OVERDUE → {@code MAIL_ADMIN_EMAIL}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisiteQuadriReminderTask {

    public static final String JOB_KEY = "VISITE_QUADRI";
    private static final DateTimeFormatter DUE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ScheduledJobSupport scheduledJobSupport;
    private final AppSettingsService appSettingsService;
    private final AtelierRepository atelierRepository;
    private final VisiteQuadriService visiteQuadriService;
    private final TransactionalMail transactionalMail;

    @Scheduled(cron = "${app.schedule.tick-cron:0 * * * * *}")
    @Transactional
    public void run() {
        if (!scheduledJobSupport.isSchedulingGloballyEnabled()) {
            return;
        }
        if (!appSettingsService.getBoolean(AppSettingsService.SCHED_VISITE_QUADRI_ENABLED, true)) {
            return;
        }
        if (!scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_VISITE_QUADRI_HOUR,
                AppSettingsService.SCHED_VISITE_QUADRI_MINUTE,
                8, 0)) {
            return;
        }

        String recipient = transactionalMail.getAdminEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            log.warn("Rappel visite quadri ignoré — MAIL_ADMIN_EMAIL invalide");
            return;
        }

        String periodKey = scheduledJobSupport.periodKeyToday();
        if (scheduledJobSupport.alreadySent(JOB_KEY, periodKey, recipient)) {
            return;
        }

        int warnDays = visiteQuadriService.resolveWarnDays();
        List<VisiteQuadriReminderEmail.ObligationLine> lines = new ArrayList<>();
        for (Atelier atelier : atelierRepository.findAll()) {
            if (!atelier.isUtilise()) {
                continue;
            }
            List<VisiteQuadriObligationResponse> alerts =
                    visiteQuadriService.listAlertObligationsForAtelier(
                            atelier.getId(), warnDays, scheduledJobSupport.today());
            for (VisiteQuadriObligationResponse o : alerts) {
                lines.add(new VisiteQuadriReminderEmail.ObligationLine(
                        atelier.getNom(),
                        o.getSfmNom(),
                        o.getMarqueLabel(),
                        o.getLevel(),
                        o.getDaysRemaining() == null ? 0L : o.getDaysRemaining(),
                        o.getDueDate() == null ? null : o.getDueDate().format(DUE_FMT)));
            }
        }

        if (lines.isEmpty()) {
            log.debug("Rappel visite quadri — aucune obligation WARN/OVERDUE (period={})", periodKey);
            return;
        }

        if (!scheduledJobSupport.tryClaim(JOB_KEY, periodKey, recipient)) {
            return;
        }
        RenderedEmail email = VisiteQuadriReminderEmail.render(
                new VisiteQuadriReminderEmail.Context(periodKey, warnDays, lines));
        EmailSendResult result = transactionalMail.notifyAdminVisiteQuadriReminder(
                email.subject(), email.text(), email.html());
        if (result.ok()) {
            log.info("Rappel visite quadri envoyé period={} lines={} simulated={}",
                    periodKey, lines.size(), result.skipped());
        } else {
            scheduledJobSupport.releaseClaim(JOB_KEY, periodKey, recipient);
            log.warn("Rappel visite quadri échec period={} error={}", periodKey, result.error());
        }
    }
}
