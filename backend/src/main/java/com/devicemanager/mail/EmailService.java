package com.devicemanager.mail;

import com.devicemanager.service.AppSettingsService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transport SMTP uniquement — ne jamais throw ; timeouts anti-502.
 * <p>
 * Activation : {@code APP_MAIL_ENABLED=true} et {@code MAIL_HOST} renseigné (ex. Brevo
 * {@code smtp-relay.brevo.com}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final AppSettingsService appSettingsService;
    private final JavaMailSender fallbackMailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabledDefault;

    @Value("${app.mail.from:noreply@devicemanager.local}")
    private String fromDefault;

    private volatile JavaMailSender dynamicSender;

    /**
     * Envoie un e-mail multipart (texte + HTML). Ne throw jamais.
     */
    public EmailSendResult send(String to, String subject, String text, String html) {
        String from = resolveFrom();
        if (!isSmtpActive()) {
            logSimulated(text);
            return EmailSendResult.simulated();
        }
        String configError = validateConfig(from, to);
        if (configError != null) {
            return EmailSendResult.failure(configError);
        }
        try {
            JavaMailSender sender = resolveSender();
            CompletableFuture<Void> sendFuture = CompletableFuture.runAsync(() -> {
                try {
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                    helper.setFrom(from);
                    helper.setTo(to);
                    helper.setSubject(subject);
                    if (html != null && !html.isBlank()) {
                        helper.setText(text, html);
                    } else {
                        helper.setText(text, false);
                    }
                    sender.send(message);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            sendFuture.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Email envoyé avec succès");
            return EmailSendResult.success();
        } catch (TimeoutException ex) {
            invalidateSender();
            log.error("Timeout SMTP ({} s)", SEND_TIMEOUT_SECONDS);
            return EmailSendResult.failure("Timeout SMTP (" + SEND_TIMEOUT_SECONDS + " s)");
        } catch (Exception ex) {
            invalidateSender();
            String message = rootMessage(ex);
            log.error("Échec envoi email: {}", sanitizeForLog(message));
            return EmailSendResult.failure(message);
        }
    }

    public boolean isSmtpActive() {
        if (!appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, mailEnabledDefault)) {
            return false;
        }
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "").trim();
        return !host.isBlank();
    }

    private String resolveFrom() {
        return appSettingsService.get(AppSettingsService.MAIL_FROM, fromDefault);
    }

    private String validateConfig(String from, String to) {
        if (from == null || from.isBlank() || !from.contains("@")) {
            return "Adresse d'expéditeur invalide. Saisissez un e-mail valide dans Paramètres.";
        }
        if (to == null || to.isBlank() || !to.contains("@")) {
            return "Adresse destinataire invalide.";
        }
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "").trim();
        if (host.isBlank()) {
            return "Serveur de messagerie manquant. Renseignez l'hôte SMTP dans Paramètres.";
        }
        String username = appSettingsService.get(AppSettingsService.MAIL_USERNAME, "").trim();
        String password = appSettingsService.get(AppSettingsService.MAIL_PASSWORD, "").trim();
        if (username.isBlank() || password.isBlank()) {
            return "Identifiants messagerie incomplets. Renseignez l'utilisateur et le mot de passe SMTP dans Paramètres.";
        }
        return null;
    }

    private JavaMailSender resolveSender() {
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "").trim();
        if (host.isBlank()) {
            return fallbackMailSender;
        }
        JavaMailSender cached = dynamicSender;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dynamicSender != null) {
                return dynamicSender;
            }
            dynamicSender = buildSender(host);
            return dynamicSender;
        }
    }

    private JavaMailSender buildSender(String host) {
        int port = (int) appSettingsService.getLong(AppSettingsService.MAIL_PORT, 587);
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setUsername(appSettingsService.get(AppSettingsService.MAIL_USERNAME, "").trim());
        impl.setPassword(appSettingsService.get(AppSettingsService.MAIL_PASSWORD, "").trim());

        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return impl;
    }

    private void invalidateSender() {
        dynamicSender = null;
    }

    private void logSimulated(String text) {
        // Ne pas journaliser destinataire / sujet / corps (PII + injection de logs).
        log.info(
                "EMAIL SIMULÉ (messagerie inactive ou MAIL_HOST vide) — envoi non effectué (bodyLength={})",
                text != null ? text.length() : 0);
    }

    private static String sanitizeForLog(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : ex.getClass().getSimpleName();
    }
}
