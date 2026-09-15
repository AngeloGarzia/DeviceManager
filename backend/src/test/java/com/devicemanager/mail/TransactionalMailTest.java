package com.devicemanager.mail;

import com.devicemanager.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalMailTest {

    @Mock private EmailService emailService;
    @Mock private AppSettingsService appSettingsService;
    @InjectMocks private TransactionalMail transactionalMail;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionalMail, "adminEmailDefault", "admin@test.local");
    }

    @Test
    void sendSmtpTest_requiresAdminEmail() {
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("");

        assertThatThrownBy(() -> transactionalMail.sendSmtpTest())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("e-mail administrateur");
                });
    }

    @Test
    void sendSmtpTest_reportsSimulatedWhenSkipped() {
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("admin@test.local");
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(EmailSendResult.simulated());

        var response = transactionalMail.sendSmtpTest();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTo()).isEqualTo("admin@test.local");
        assertThat(response.getMessage()).contains("simulé");
    }

    @Test
    void sendPasswordReset_delegatesToEmailService() {
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(EmailSendResult.success());

        EmailSendResult result = transactionalMail.sendPasswordReset(
                "user@test.local",
                new com.devicemanager.mail.templates.PasswordResetEmail.Context(
                        "Marie", "http://localhost:4200/reset-password?token=x"));

        assertThat(result.ok()).isTrue();
    }
}
