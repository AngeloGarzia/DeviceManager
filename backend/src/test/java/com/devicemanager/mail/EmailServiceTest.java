package com.devicemanager.mail;

import com.devicemanager.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private AppSettingsService appSettingsService;
    @Mock private JavaMailSender fallbackMailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(appSettingsService, fallbackMailSender);
        ReflectionTestUtils.setField(emailService, "mailEnabledDefault", false);
        ReflectionTestUtils.setField(emailService, "fromDefault", "from@test.local");
    }

    @Test
    void send_skipsWhenMailDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(false);

        EmailSendResult result = emailService.send("admin@test.local", "Sujet", "Corps", "<p>Corps</p>");

        assertThat(result.ok()).isTrue();
        assertThat(result.skipped()).isTrue();
        verify(fallbackMailSender, never()).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
    }

    @Test
    void send_returnsFailureWhenCredentialsMissing() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_HOST), anyString())).thenReturn("smtp-relay.brevo.com");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_USERNAME), anyString())).thenReturn("");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_PASSWORD), anyString())).thenReturn("");

        EmailSendResult result = emailService.send("admin@test.local", "Sujet", "Corps", null);

        assertThat(result.ok()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.error()).contains("Identifiants messagerie");
    }
}
