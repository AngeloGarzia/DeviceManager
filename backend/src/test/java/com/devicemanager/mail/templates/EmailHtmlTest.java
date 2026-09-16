package com.devicemanager.mail.templates;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlTest {

    @Test
    void emailShell_includesResponsiveNota() {
        String html = EmailHtml.emailShell("Titre", "<p>Corps</p>", null, null);

        assertThat(html).contains("conçu pour toutes les tailles");
        assertThat(html).contains("tous types d&#39;appareils");
    }

    @Test
    void appendResponsiveNotaText_appendsOnce() {
        String once = EmailHtml.appendResponsiveNotaText("Bonjour,\n");
        String twice = EmailHtml.appendResponsiveNotaText(once);

        assertThat(once).contains(EmailHtml.RESPONSIVE_NOTA);
        assertThat(twice).isEqualTo(once.endsWith("\n") ? once : once + "\n");
        assertThat(twice.indexOf(EmailHtml.RESPONSIVE_NOTA))
                .isEqualTo(twice.lastIndexOf(EmailHtml.RESPONSIVE_NOTA));
    }
}
