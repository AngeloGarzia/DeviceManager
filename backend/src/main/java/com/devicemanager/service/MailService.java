package com.devicemanager.service;

import com.devicemanager.dto.MailTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final AppSettingsService appSettingsService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabledDefault;

    @Value("${app.mail.from:noreply@devicemanager.local}")
    private String fromDefault;

    @Value("${app.mail.admin-email:admin@casino.local}")
    private String adminEmailDefault;

    public void sendOrderRequestToAdmin(String subject, String body) {
        send(getAdminEmail(), subject, body);
    }

    public MailTestResponse sendTestEmail() {
        String to = getAdminEmail();
        if (to == null || to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Configurez MAIL_ADMIN_EMAIL (destinataire) avant le test");
        }
        try {
            send(to, "DeviceManager — test SMTP", """
                    Bonjour,

                    Ceci est un e-mail de test envoyé depuis DeviceManager.
                    Si vous le recevez, la messagerie est correctement configurée.

                    — DeviceManager
                    """);
            return MailTestResponse.builder()
                    .success(true)
                    .to(to)
                    .message("E-mail de test envoyé à " + to)
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec e-mail de test: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Échec SMTP: " + rootMessage(ex));
        }
    }

    public void send(String to, String subject, String body) {
        boolean mailEnabled = appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, mailEnabledDefault);
        String from = appSettingsService.get(AppSettingsService.MAIL_FROM, fromDefault);

        if (!mailEnabled) {
            log.info("""
                    ===== EMAIL SIMULÉ (MAIL_ENABLED=false) =====
                    To: {}
                    From: {}
                    Subject: {}
                    {}
                    ==============================================
                    """, to, from, subject, body);
            return;
        }

        validateSmtpConfig(from, to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        JavaMailSender sender = resolveSender();
        sender.send(message);
        log.info("Email envoyé à {} (sujet={})", to, subject);
    }

    public String getAdminEmail() {
        return appSettingsService.get(AppSettingsService.MAIL_ADMIN_EMAIL, adminEmailDefault);
    }

    private void validateSmtpConfig(String from, String to) {
        if (from == null || from.isBlank() || !from.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MAIL_FROM invalide — utilisez une adresse e-mail réelle");
        }
        if (to == null || to.isBlank() || !to.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MAIL_ADMIN_EMAIL invalide");
        }
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "");
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MAIL_HOST manquant (ex. smtp-relay.brevo.com ou smtp.gmail.com)");
        }
        String username = appSettingsService.get(AppSettingsService.MAIL_USERNAME, "");
        String password = appSettingsService.get(AppSettingsService.MAIL_PASSWORD, "");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MAIL_USERNAME / MAIL_PASSWORD requis pour l'envoi SMTP");
        }
    }

    private JavaMailSender resolveSender() {
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "");
        if (host == null || host.isBlank()) {
            return mailSender;
        }
        int port = (int) appSettingsService.getLong(AppSettingsService.MAIL_PORT, 587);
        JavaMailSenderImpl dynamic = new JavaMailSenderImpl();
        dynamic.setHost(host);
        dynamic.setPort(port);
        dynamic.setUsername(appSettingsService.get(AppSettingsService.MAIL_USERNAME, ""));
        dynamic.setPassword(appSettingsService.get(AppSettingsService.MAIL_PASSWORD, ""));

        Properties props = dynamic.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return dynamic;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg;
    }
}
