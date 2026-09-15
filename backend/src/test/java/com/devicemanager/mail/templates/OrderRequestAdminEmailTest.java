package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestAdminEmailTest {

    @Test
    void render_includesSubjectTextAndHtml() {
        OrderRequestAdminEmail.Context ctx = new OrderRequestAdminEmail.Context(
                42L,
                "Atelier Nord",
                "Jean Dupont",
                LocalDateTime.of(2026, 3, 15, 10, 30),
                List.of(new OrderRequestAdminEmail.LineItem("Carte mère", "REF-1", 2, "SFM Nord")),
                List.of("SFM Nord <sfm@example.com>"),
                "Urgent");

        RenderedEmail email = OrderRequestAdminEmail.render(ctx);

        assertThat(email.subject()).contains("#42").contains("1 pièce");
        assertThat(email.text()).contains("Jean Dupont").contains("Urgent").contains("Carte mère");
        assertThat(email.html()).contains("DeviceManager").contains("Carte mère");
    }
}
