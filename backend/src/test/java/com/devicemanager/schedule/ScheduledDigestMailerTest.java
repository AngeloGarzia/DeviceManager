package com.devicemanager.schedule;

import com.devicemanager.entity.Casino;
import com.devicemanager.entity.Groupe;
import com.devicemanager.mail.EmailSendResult;
import com.devicemanager.mail.RenderedEmail;
import com.devicemanager.mail.TransactionalMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledDigestMailerTest {

    @Mock private ScheduledJobSupport scheduledJobSupport;
    @Mock private ScheduledAdminRecipientService recipientService;
    @Mock private TransactionalMail transactionalMail;
    @InjectMocks private ScheduledDigestMailer mailer;

    @Test
    void sendToCasinoAdmins_sendsPerRecipientWithCasinoPeriodKey() {
        Casino casino = Casino.builder()
                .id(5L)
                .nom("Casino X")
                .groupe(Groupe.builder().id(1L).nom("G").build())
                .build();
        when(recipientService.emailsForCasino(casino)).thenReturn(List.of("a@x.local", "b@x.local"));
        when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.sendScheduledReminder(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success());

        RenderedEmail email = new RenderedEmail("Sujet", "text", "<p>html</p>");
        int sent = mailer.sendToCasinoAdmins("JOB", "2026-09-17", casino, email);

        assertThat(sent).isEqualTo(2);
        verify(scheduledJobSupport).tryClaim("JOB", "2026-09-17|casino:5", "a@x.local");
        verify(scheduledJobSupport).tryClaim("JOB", "2026-09-17|casino:5", "b@x.local");
        verify(transactionalMail).sendScheduledReminder(
                eq("a@x.local"), eq("Sujet — Casino X"), anyString(), eq("<p>html</p>"));
        verify(transactionalMail).sendScheduledReminder(
                eq("b@x.local"), eq("Sujet — Casino X"), anyString(), eq("<p>html</p>"));
    }

    @Test
    void sendToCasinoAdmins_noRecipients_skips() {
        Casino casino = Casino.builder().id(5L).nom("X").build();
        when(recipientService.emailsForCasino(casino)).thenReturn(List.of());

        int sent = mailer.sendToCasinoAdmins(
                "JOB", "2026-09-17", casino, new RenderedEmail("S", "t", "h"));

        assertThat(sent).isZero();
        verify(transactionalMail, never()).sendScheduledReminder(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendToCasinoAdmins_releasesClaimOnFailure() {
        Casino casino = Casino.builder().id(5L).nom("X").build();
        when(recipientService.emailsForCasino(casino)).thenReturn(List.of("a@x.local"));
        when(scheduledJobSupport.alreadySent(anyString(), anyString(), anyString())).thenReturn(false);
        when(scheduledJobSupport.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(transactionalMail.sendScheduledReminder(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failure("SMTP"));

        mailer.sendToCasinoAdmins("JOB", "2026-09-17", casino, new RenderedEmail("S", "t", "h"));

        verify(scheduledJobSupport).releaseClaim("JOB", "2026-09-17|casino:5", "a@x.local");
    }
}
