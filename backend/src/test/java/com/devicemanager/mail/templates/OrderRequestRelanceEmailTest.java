package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestRelanceEmailTest {

    @Test
    void render_includesOrders() {
        RenderedEmail email = OrderRequestRelanceEmail.render(new OrderRequestRelanceEmail.Context(
                "2026-09-17",
                7,
                List.of(new OrderRequestRelanceEmail.OrderLine(
                        42L, "Atelier A", "Tech Un", "PENDING", "01/09/2026 10:00", 16, 3))));

        assertThat(email.subject()).contains("1 demande").contains("7 j");
        assertThat(email.text()).contains("#42").contains("Atelier A");
        assertThat(email.html()).contains("DeviceManager").contains("42");
    }

    @Test
    void render_emptyAndEscapes() {
        RenderedEmail empty = OrderRequestRelanceEmail.render(
                new OrderRequestRelanceEmail.Context("2026-09-17", 7, List.of()));
        assertThat(empty.text()).contains("(aucune)");

        RenderedEmail escaped = OrderRequestRelanceEmail.render(new OrderRequestRelanceEmail.Context(
                "2026-09-17",
                7,
                List.of(new OrderRequestRelanceEmail.OrderLine(
                        1L, "A <B>", "T", "PENDING", "01/09/2026", 8, 1))));
        assertThat(escaped.html()).contains("A &lt;B&gt;");
    }
}
