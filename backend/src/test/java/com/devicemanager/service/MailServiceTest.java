package com.devicemanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private AppSettingsService appSettingsService;
    @InjectMocks private MailService mailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "mailEnabledDefault", false);
        ReflectionTestUtils.setField(mailService, "fromDefault", "from@test.local");
        ReflectionTestUtils.setField(mailService, "adminEmailDefault", "admin@test.local");
    }

    @Test
    void sendOrderRequestToAdmin_simulatesWhenDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(false);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("admin@test.local");

        mailService.sendOrderRequestToAdmin("Sujet", "Corps");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_rejectsMissingSmtpHost() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_HOST), anyString())).thenReturn("");

        assertThatThrownBy(() -> mailService.send("admin@test.local", "S", "B"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("MAIL_HOST");
                });
    }

    @Test
    void send_rejectsMissingCredentials() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_HOST), anyString())).thenReturn("smtp.test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_USERNAME), anyString())).thenReturn("");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_PASSWORD), anyString())).thenReturn("");

        assertThatThrownBy(() -> mailService.send("admin@test.local", "S", "B"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("MAIL_USERNAME");
    }

    @Test
    void sendTestEmail_requiresAdminEmail() {
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("");

        assertThatThrownBy(() -> mailService.sendTestEmail())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("MAIL_ADMIN_EMAIL");
    }
}
