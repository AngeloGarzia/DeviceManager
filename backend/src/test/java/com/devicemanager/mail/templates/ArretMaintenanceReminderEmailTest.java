package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArretMaintenanceReminderEmailTest {

    @Test
    void render_includesArrets() {
        RenderedEmail email = ArretMaintenanceReminderEmail.render(new ArretMaintenanceReminderEmail.Context(
                "2026-09-17",
                3,
                List.of(new ArretMaintenanceReminderEmail.ArretLine(
                        9L, "Atelier A", "MAS-001", "Carte mère", "10/09/2026 09:00", 7))));

        assertThat(email.subject()).contains("1 ouvert").contains("3 j");
        assertThat(email.text()).contains("MAS-001").contains("Carte mère");
        assertThat(email.html()).contains("DeviceManager").contains("MAS-001");
    }

    @Test
    void render_emptyAndEscapes() {
        assertThat(ArretMaintenanceReminderEmail.render(
                new ArretMaintenanceReminderEmail.Context("2026-09-17", 3, List.of()))
                .text()).contains("(aucun)");

        RenderedEmail escaped = ArretMaintenanceReminderEmail.render(new ArretMaintenanceReminderEmail.Context(
                "2026-09-17",
                3,
                List.of(new ArretMaintenanceReminderEmail.ArretLine(
                        1L, "A&B", "MAS", "motif <x>", "10/09/2026", 4))));
        assertThat(escaped.html()).contains("A&amp;B").contains("motif &lt;x&gt;");
    }
}
