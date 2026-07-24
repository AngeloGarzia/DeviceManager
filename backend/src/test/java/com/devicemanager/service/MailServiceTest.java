package com.devicemanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    private static final Logger log = LoggerFactory.getLogger(MailServiceTest.class);

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
        log.info("Test mail simulé");
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(false);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("admin@test.local");

        mailService.sendOrderRequestToAdmin("Sujet", "Corps");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendOrderRequestToAdmin_sendsWhenEnabled() {
        when(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(eq(AppSettingsService.MAIL_FROM), anyString())).thenReturn("from@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_ADMIN_EMAIL), anyString())).thenReturn("admin@test.local");
        when(appSettingsService.get(eq(AppSettingsService.MAIL_HOST), anyString())).thenReturn("");

        mailService.sendOrderRequestToAdmin("Sujet", "Corps");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
