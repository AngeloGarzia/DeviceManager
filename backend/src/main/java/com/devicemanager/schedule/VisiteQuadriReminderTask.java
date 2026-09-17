package com.devicemanager.schedule;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.mail.RenderedEmail;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rappel quotidien des visites quadritrimestrielles WARN / OVERDUE
 * → ADMIN / SUPER_ADMIN du casino concerné.
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
    private final ScheduledDigestMailer digestMailer;

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

        int warnDays = visiteQuadriService.resolveWarnDays();
        String dateKey = scheduledJobSupport.periodKeyToday();

        Map<Long, Casino> casinos = new LinkedHashMap<>();
        Map<Long, List<VisiteQuadriReminderEmail.ObligationLine>> linesByCasino = new LinkedHashMap<>();

        for (Atelier atelier : atelierRepository.findAllActiveWithCasino()) {
            Casino casino = atelier.getCasino();
            if (casino == null || casino.getId() == null) {
                continue;
            }
            List<VisiteQuadriObligationResponse> alerts =
                    visiteQuadriService.listAlertObligationsForAtelier(
                            atelier.getId(), warnDays, scheduledJobSupport.today());
            if (alerts.isEmpty()) {
                continue;
            }
            casinos.putIfAbsent(casino.getId(), casino);
            List<VisiteQuadriReminderEmail.ObligationLine> lines =
                    linesByCasino.computeIfAbsent(casino.getId(), id -> new ArrayList<>());
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

        if (linesByCasino.isEmpty()) {
            log.debug("Rappel visite quadri — aucune obligation WARN/OVERDUE");
            return;
        }

        for (Map.Entry<Long, List<VisiteQuadriReminderEmail.ObligationLine>> entry : linesByCasino.entrySet()) {
            Casino casino = casinos.get(entry.getKey());
            RenderedEmail email = VisiteQuadriReminderEmail.render(
                    new VisiteQuadriReminderEmail.Context(dateKey, warnDays, entry.getValue()));
            digestMailer.sendToCasinoAdmins(JOB_KEY, dateKey, casino, email);
        }
    }
}
