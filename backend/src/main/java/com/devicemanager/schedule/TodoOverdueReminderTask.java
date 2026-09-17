package com.devicemanager.schedule;

import com.devicemanager.entity.TodoTache;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
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

/**
 * Rappel quotidien des todos en retard → {@code MAIL_ADMIN_EMAIL}.
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
    private final TransactionalMail transactionalMail;

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

        String recipient = transactionalMail.getAdminEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            log.warn("Rappel todos en retard ignoré — MAIL_ADMIN_EMAIL invalide");
            return;
        }

        String periodKey = scheduledJobSupport.periodKeyToday();
        if (scheduledJobSupport.alreadySent(JOB_KEY, periodKey, recipient)) {
            return;
        }

        LocalDateTime now = scheduledJobSupport.now();
        List<TodoTache> overdue = todoService.listOverdueForReminder(now);
        if (overdue.isEmpty()) {
            log.debug("Rappel todos — aucun en retard (period={})", periodKey);
            return;
        }

        List<TodoOverdueReminderEmail.TodoLine> lines = new ArrayList<>();
        for (TodoTache t : overdue) {
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

        if (!scheduledJobSupport.tryClaim(JOB_KEY, periodKey, recipient)) {
            return;
        }
        RenderedEmail email = TodoOverdueReminderEmail.render(
                new TodoOverdueReminderEmail.Context(periodKey, lines));
        EmailSendResult result = transactionalMail.notifyAdminTodoOverdueReminder(
                email.subject(), email.text(), email.html());
        if (result.ok()) {
            log.info("Rappel todos en retard envoyé period={} lines={} simulated={}",
                    periodKey, lines.size(), result.skipped());
        } else {
            scheduledJobSupport.releaseClaim(JOB_KEY, periodKey, recipient);
            log.warn("Rappel todos en retard échec period={} error={}", periodKey, result.error());
        }
    }
}
