package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisiteQuadriReminderEmailTest {

    @Test
    void render_includesCountsAndLines() {
        RenderedEmail email = VisiteQuadriReminderEmail.render(new VisiteQuadriReminderEmail.Context(
                "2026-09-17",
                7,
                List.of(
                        new VisiteQuadriReminderEmail.ObligationLine(
                                "Atelier A", "SFM Est", "Novomatic", "OVERDUE", -2, "15/09/2026"),
                        new VisiteQuadriReminderEmail.ObligationLine(
                                "Atelier A", "SFM Est", "Aristocrat", "WARN", 3, "20/09/2026"))));

        assertThat(email.subject()).contains("1 en retard").contains("1 à échéance");
        assertThat(email.text()).contains("SFM Est").contains("Novomatic").contains("Aristocrat");
        assertThat(email.html()).contains("EN RETARD").contains("DeviceManager");
    }

    @Test
    void render_emptyLines() {
        RenderedEmail email = VisiteQuadriReminderEmail.render(
                new VisiteQuadriReminderEmail.Context("2026-09-17", 7, List.of()));
        assertThat(email.subject()).contains("0 en retard");
        assertThat(email.text()).contains("(aucune)");
    }

    @Test
    void render_escapesHtmlSpecialChars() {
        RenderedEmail email = VisiteQuadriReminderEmail.render(new VisiteQuadriReminderEmail.Context(
                "2026-09-17",
                7,
                List.of(new VisiteQuadriReminderEmail.ObligationLine(
                        "A & B <C>", "SFM", "Marque", "WARN", 1, "20/09/2026"))));
        assertThat(email.html()).contains("A &amp; B &lt;C&gt;");
        assertThat(email.html()).doesNotContain("A & B <C>");
    }
}
