package com.devicemanager.mail;

import com.devicemanager.dto.MailTestResponse;
import com.devicemanager.mail.templates.OrderRequestAdminEmail;
import com.devicemanager.mail.templates.OrderRequestSfmEmail;
import com.devicemanager.mail.templates.PasswordResetEmail;
import com.devicemanager.mail.templates.PasswordWelcomeEmail;
import com.devicemanager.mail.templates.SmtpTestEmail;
import com.devicemanager.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Façade métier des e-mails transactionnels — ne fait jamais planter l'appelant (sauf test SMTP admin).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalMail {

    private final EmailService emailService;
    private final AppSettingsService appSettingsService;

    @Value("${app.mail.admin-email:admin@casino.local}")
    private String adminEmailDefault;

    public String getAdminEmail() {
        return appSettingsService.get(AppSettingsService.MAIL_ADMIN_EMAIL, adminEmailDefault);
    }

    /**
     * Envoie un rappel planifié à un destinataire explicite (ADMIN / SUPER_ADMIN casino).
     */
    public EmailSendResult sendScheduledReminder(String to, String subject, String text, String html) {
        EmailSendResult result = emailService.send(to, subject, text, html);
        if (!result.ok()) {
            log.warn("E-mail rappel planifié non envoyé à {}: {}", to, result.error());
        }
        return result;
    }

    /**
     * Notification admin — nouvelle demande de commande (destinataires casino).
     */
    public EmailSendResult notifyAdminNewOrderRequest(OrderRequestAdminEmail.Context context, List<String> recipients) {
        RenderedEmail email = OrderRequestAdminEmail.render(context);
        if (recipients == null || recipients.isEmpty()) {
            String fallback = getAdminEmail();
            return sendToOne(fallback, email, context.orderId());
        }
        EmailSendResult last = EmailSendResult.failure("aucun destinataire");
        for (String to : recipients) {
            last = sendToOne(to, email, context.orderId());
        }
        return last;
    }

    /**
     * Notification admin — nouvelle demande de commande (legacy : MAIL_ADMIN_EMAIL).
     */
    public EmailSendResult notifyAdminNewOrderRequest(OrderRequestAdminEmail.Context context) {
        return notifyAdminNewOrderRequest(context, List.of(getAdminEmail()));
    }

    private EmailSendResult sendToOne(String to, RenderedEmail email, Long orderId) {
        EmailSendResult result = emailService.send(to, email.subject(), email.text(), email.html());
        if (!result.ok()) {
            log.warn("E-mail admin non envoyé (demande {}): {}", orderId, result.error());
        }
        return result;
    }

    /**
     * Rappel admin — todos en retard.
     * @deprecated utiliser {@link #sendScheduledReminder}
     */
    @Deprecated
    public EmailSendResult notifyAdminTodoOverdueReminder(String subject, String text, String html) {
        return sendScheduledReminder(getAdminEmail(), subject, text, html);
    }

    /**
     * Rappel admin — arrêts maintenance ouverts trop longtemps.
     * @deprecated utiliser {@link #sendScheduledReminder}
     */
    @Deprecated
    public EmailSendResult notifyAdminArretMaintenanceReminder(String subject, String text, String html) {
        return sendScheduledReminder(getAdminEmail(), subject, text, html);
    }

    /**
     * Relance admin — commandes PENDING/SENT trop anciennes.
     * @deprecated utiliser {@link #sendScheduledReminder}
     */
    @Deprecated
    public EmailSendResult notifyAdminOrderRelance(String subject, String text, String html) {
        return sendScheduledReminder(getAdminEmail(), subject, text, html);
    }

    /**
     * Rappel admin — visites quadritrimestrielles à échéance / en retard.
     * @deprecated utiliser {@link #sendScheduledReminder}
     */
    @Deprecated
    public EmailSendResult notifyAdminVisiteQuadriReminder(String subject, String text, String html) {
        return sendScheduledReminder(getAdminEmail(), subject, text, html);
    }

    /**
     * Demande de devis SFM — validation admin.
     */
    public EmailSendResult notifySfmOrderValidated(String to, OrderRequestSfmEmail.Context context) {
        RenderedEmail email = OrderRequestSfmEmail.render(context);
        EmailSendResult result = emailService.send(to, email.subject(), email.text(), email.html());
        if (!result.ok()) {
            log.warn("E-mail SFM non envoyé à {} (demande {}): {}", to, context.orderId(), result.error());
        }
        return result;
    }

    /**
     * Lien de réinitialisation de mot de passe (échec logué, non bloquant).
     */
    public EmailSendResult sendPasswordReset(String to, PasswordResetEmail.Context context) {
        RenderedEmail email = PasswordResetEmail.render(context);
        EmailSendResult result = emailService.send(to, email.subject(), email.text(), email.html());
        if (!result.ok()) {
            log.warn("E-mail reset mot de passe non envoyé à {}: {}", to, result.error());
        }
        return result;
    }

    /**
     * Bienvenue admin → utilisateur (identifiant + MDP temporaire + lien reset).
     * Remonte une erreur HTTP si messagerie inactive ou échec SMTP.
     */
    public void sendUserWelcome(String to, PasswordWelcomeEmail.Context context) {
        RenderedEmail email = PasswordWelcomeEmail.render(context);
        EmailSendResult result = emailService.send(to, email.subject(), email.text(), email.html());
        if (result.skipped()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Messagerie désactivée ou MAIL_HOST vide. Activez-la dans Paramètres avant d'envoyer.");
        }
        if (!result.ok()) {
            throw mapSendFailure(result.error());
        }
    }

    /**
     * Test SMTP depuis Setup — remonte 400/502 si échec réel (pas en mode simulé).
     */
    public MailTestResponse sendSmtpTest() {
        String to = getAdminEmail();
        if (to == null || to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indiquez l'e-mail administrateur dans Paramètres avant d'envoyer un test.");
        }
        RenderedEmail email = SmtpTestEmail.render();
        EmailSendResult result = emailService.send(to, email.subject(), email.text(), email.html());
        if (result.skipped()) {
            return MailTestResponse.builder()
                    .success(true)
                    .to(to)
                    .message("E-mail simulé (messagerie désactivée ou MAIL_HOST vide). Consultez les logs serveur.")
                    .build();
        }
        if (!result.ok()) {
            throw mapSendFailure(result.error());
        }
        return MailTestResponse.builder()
                .success(true)
                .to(to)
                .message("E-mail de test envoyé à " + to)
                .build();
    }

    private ResponseStatusException mapSendFailure(String error) {
        String msg = error == null ? "Échec d'envoi" : error;
        if (msg.contains("Serveur de messagerie")
                || msg.contains("Identifiants messagerie")
                || msg.contains("expéditeur")
                || msg.contains("destinataire")) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Échec d'envoi de l'e-mail. Vérifiez la configuration messagerie dans Paramètres.");
    }
}
