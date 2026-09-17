package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoOverdueReminderEmailTest {

    @Test
    void render_includesTodos() {
        RenderedEmail email = TodoOverdueReminderEmail.render(new TodoOverdueReminderEmail.Context(
                "2026-09-17",
                List.of(new TodoOverdueReminderEmail.TodoLine(
                        5L, "Centre", "Vérifier MAS", "HIGH", "10/09/2026 08:00", 7, "MAS-001"))));

        assertThat(email.subject()).contains("1 tâche");
        assertThat(email.text()).contains("Vérifier MAS").contains("MAS-001");
        assertThat(email.html()).contains("DeviceManager").contains("HIGH");
    }

    @Test
    void render_emptyAndEscapes() {
        assertThat(TodoOverdueReminderEmail.render(
                new TodoOverdueReminderEmail.Context("2026-09-17", List.of()))
                .text()).contains("(aucune)");

        RenderedEmail escaped = TodoOverdueReminderEmail.render(new TodoOverdueReminderEmail.Context(
                "2026-09-17",
                List.of(new TodoOverdueReminderEmail.TodoLine(
                        1L, "A <B>", "Titre & co", "HIGH", "10/09/2026", 2, null))));
        assertThat(escaped.html()).contains("A &lt;B&gt;").contains("Titre &amp; co");
    }
}
