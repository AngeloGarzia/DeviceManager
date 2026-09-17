package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Envoie un digest planifié par casino aux ADMIN / SUPER_ADMIN concernés.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledDigestMailer {

    private final ScheduledJobSupport scheduledJobSupport;
    private final ScheduledAdminRecipientService recipientService;
    private final TransactionalMail transactionalMail;

    /** Regroupe des entités par casino (via leur atelier). */
    public static <T> Map<Casino, List<T>> groupByCasino(List<T> items, Function<T, Atelier> atelierFn) {
        Map<Long, Casino> casinos = new LinkedHashMap<>();
        Map<Long, List<T>> buckets = new LinkedHashMap<>();
        if (items == null) {
            return Map.of();
        }
        for (T item : items) {
            Atelier atelier = atelierFn.apply(item);
            if (atelier == null || atelier.getCasino() == null || atelier.getCasino().getId() == null) {
                log.warn("Digest planifié — entité sans casino, ignorée");
                continue;
            }
            Casino casino = atelier.getCasino();
            casinos.putIfAbsent(casino.getId(), casino);
            buckets.computeIfAbsent(casino.getId(), id -> new ArrayList<>()).add(item);
        }
        Map<Casino, List<T>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<T>> e : buckets.entrySet()) {
            result.put(casinos.get(e.getKey()), e.getValue());
        }
        return result;
    }

    /**
     * Envoie le mail à chaque ADMIN/SUPER_ADMIN concerné par le casino.
     * Idempotence : {@code periodKey = dateKey|casino:id} × destinataire.
     *
     * @return nombre d'envois OK (ou simulés)
     */
    public int sendToCasinoAdmins(String jobKey, String dateKey, Casino casino, RenderedEmail email) {
        if (casino == null || casino.getId() == null || email == null) {
            return 0;
        }
        List<String> recipients = recipientService.emailsForCasino(casino);
        if (recipients.isEmpty()) {
            log.warn("Digest {} casino={} ({}) — aucun ADMIN/SUPER_ADMIN avec e-mail",
                    jobKey, casino.getId(), casino.getNom());
            return 0;
        }
        String periodKey = dateKey + "|casino:" + casino.getId();
        String subject = withCasino(email.subject(), casino);
        int sent = 0;
        for (String to : recipients) {
            if (scheduledJobSupport.alreadySent(jobKey, periodKey, to)) {
                continue;
            }
            if (!scheduledJobSupport.tryClaim(jobKey, periodKey, to)) {
                continue;
            }
            EmailSendResult result = transactionalMail.sendScheduledReminder(
                    to, subject, email.text(), email.html());
            if (result.ok()) {
                sent++;
                log.info("Digest {} envoyé casino={} to={} simulated={}",
                        jobKey, casino.getId(), to, result.skipped());
            } else {
                scheduledJobSupport.releaseClaim(jobKey, periodKey, to);
                log.warn("Digest {} échec casino={} to={} error={}",
                        jobKey, casino.getId(), to, result.error());
            }
        }
        return sent;
    }

    private static String withCasino(String subject, Casino casino) {
        String nom = casino.getNom() == null || casino.getNom().isBlank()
                ? ("#" + casino.getId())
                : casino.getNom();
        if (subject == null || subject.isBlank()) {
            return nom;
        }
        return subject + " — " + nom;
    }
}
