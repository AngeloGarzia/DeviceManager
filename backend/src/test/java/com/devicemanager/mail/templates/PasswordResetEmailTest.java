package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetEmailTest {

    @Test
    void render_includesCtaAndEscapesName() {
        RenderedEmail email = PasswordResetEmail.render(
                new PasswordResetEmail.Context("Marie <admin>", "https://app.example/reset-password?token=abc"));

        assertThat(email.subject()).contains("réinitialisation");
        assertThat(email.text()).contains("https://app.example/reset-password?token=abc");
        assertThat(email.text()).contains("Marie <admin>");
        assertThat(email.html()).contains("Réinitialiser");
        assertThat(email.html()).contains("https://app.example/reset-password?token=abc");
        assertThat(email.html()).contains("Marie &lt;admin&gt;");
        assertThat(email.html()).doesNotContain("Marie <admin>");
    }
}
