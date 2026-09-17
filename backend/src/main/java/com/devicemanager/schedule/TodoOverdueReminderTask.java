package com.devicemanager.schedule;

import com.devicemanager.entity.Casino;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.templates.TodoOverdueReminderEmail;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.TodoService;
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
 * Rappel quotidien des todos en retard → ADMIN / SUPER_ADMIN du casino concerné.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoOverdueReminderTask {

    public static final String JOB_KEY = "TODO_OVERDUE";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ScheduledJobSupport scheduledJobSupport;
    private final AppSettingsService appSettingsService;
    private final TodoService todoService;
    private final ScheduledDigestMailer digestMailer;

    @Scheduled(cron = "${app.schedule.tick-cron:0 * * * * *}")
    @Transactional
    public void run() {
        if (!scheduledJobSupport.isSchedulingGloballyEnabled()) {
            return;
        }
        if (!appSettingsService.getBoolean(AppSettingsService.SCHED_TODO_OVERDUE_ENABLED, true)) {
            return;
        }
        if (!scheduledJobSupport.isDueToday(
                AppSettingsService.SCHED_TODO_OVERDUE_HOUR,
                AppSettingsService.SCHED_TODO_OVERDUE_MINUTE,
                8, 0)) {
            return;
        }

        LocalDateTime now = scheduledJobSupport.now();
        List<TodoTache> overdue = todoService.listOverdueForReminder(now);
        if (overdue.isEmpty()) {
            log.debug("Rappel todos — aucun en retard");
            return;
        }

        String dateKey = scheduledJobSupport.periodKeyToday();
        Map<Casino, List<TodoTache>> byCasino =
                ScheduledDigestMailer.groupByCasino(overdue, TodoTache::getAtelier);
        for (Map.Entry<Casino, List<TodoTache>> entry : byCasino.entrySet()) {
            List<TodoOverdueReminderEmail.TodoLine> lines = new ArrayList<>();
            for (TodoTache t : entry.getValue()) {
                long overdueDays = t.getDueAt() == null
                        ? 0
                        : Math.max(0, ChronoUnit.DAYS.between(t.getDueAt().toLocalDate(), now.toLocalDate()));
                lines.add(new TodoOverdueReminderEmail.TodoLine(
                        t.getId(),
                        t.getAtelier() != null ? t.getAtelier().getNom() : null,
                        t.getTitre(),
                        t.getSeverite(),
                        t.getDueAt() == null ? null : t.getDueAt().format(DATE_FMT),
                        overdueDays,
                        t.getMas() != null ? t.getMas().getNumero() : null));
            }
            RenderedEmail email = TodoOverdueReminderEmail.render(
                    new TodoOverdueReminderEmail.Context(dateKey, lines));
            digestMailer.sendToCasinoAdmins(JOB_KEY, dateKey, entry.getKey(), email);
        }
    }
}
