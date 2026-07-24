package com.devicemanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

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
        boolean mailEnabled = appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, mailEnabledDefault);
        String from = appSettingsService.get(AppSettingsService.MAIL_FROM, fromDefault);
        String adminEmail = appSettingsService.get(AppSettingsService.MAIL_ADMIN_EMAIL, adminEmailDefault);

        if (!mailEnabled) {
            log.info("""
                    ===== DEMANDE DE COMMANDE (email simulé) =====
                    To: {}
                    From: {}
                    Subject: {}
                    {}
                    ==============================================
                    """, adminEmail, from, subject, body);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(adminEmail);
        message.setSubject(subject);
        message.setText(body);

        JavaMailSender sender = resolveSender();
        sender.send(message);
        log.info("Email de demande de commande envoyé à {}", adminEmail);
    }

    public String getAdminEmail() {
        return appSettingsService.get(AppSettingsService.MAIL_ADMIN_EMAIL, adminEmailDefault);
    }

    private JavaMailSender resolveSender() {
        String host = appSettingsService.get(AppSettingsService.MAIL_HOST, "");
        if (host == null || host.isBlank()) {
            return mailSender;
        }
        JavaMailSenderImpl dynamic = new JavaMailSenderImpl();
        dynamic.setHost(host);
        dynamic.setPort((int) appSettingsService.getLong(AppSettingsService.MAIL_PORT, 587));
        dynamic.setUsername(appSettingsService.get(AppSettingsService.MAIL_USERNAME, ""));
        dynamic.setPassword(appSettingsService.get(AppSettingsService.MAIL_PASSWORD, ""));
        Properties props = dynamic.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return dynamic;
    }
}
