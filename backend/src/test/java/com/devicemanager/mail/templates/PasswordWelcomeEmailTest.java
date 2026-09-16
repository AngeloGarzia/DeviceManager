package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordWelcomeEmailTest {

    @Test
    void render_includesCredentialsAndResetLink() {
        RenderedEmail email = PasswordWelcomeEmail.render(
                new PasswordWelcomeEmail.Context(
                        "Angelo",
                        "agarzia",
                        "TmpPass1234",
                        "https://devicemanagercircus.onrender.com",
                        "https://app.example/reset-password?token=abc"));

        assertThat(email.subject()).contains("bienvenue");
        assertThat(email.text()).contains("agarzia");
        assertThat(email.text()).contains("TmpPass1234");
        assertThat(email.text()).contains("https://devicemanagercircus.onrender.com");
        assertThat(email.text()).contains("https://app.example/reset-password?token=abc");
        assertThat(email.text()).contains("24 heures");
        assertThat(email.text()).contains(EmailHtml.RESPONSIVE_NOTA);
        assertThat(email.text()).doesNotContain("Bienvenue sur DeviceManager");
        assertThat(email.html()).contains("agarzia");
        assertThat(email.html()).contains("TmpPass1234");
        assertThat(email.html()).contains("https://devicemanagercircus.onrender.com");
        assertThat(email.html()).contains("Réinitialiser mon mot de passe");
        assertThat(email.html()).contains("conçu pour toutes les tailles");
        assertThat(email.html()).doesNotContain("Bienvenue sur DeviceManager");
    }
}
