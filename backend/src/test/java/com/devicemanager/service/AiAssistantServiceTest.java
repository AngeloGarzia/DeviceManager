package com.devicemanager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private ObjectProvider<ChatClient> chatClientProvider;

    @Test
    void status_reportsDisabledWhenFlagOff() {
        AiAssistantService service = new AiAssistantService(false, chatClientProvider);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.status().isEnabled()).isFalse();
        assertThat(service.status().getReply()).contains("désactivé");
    }

    @Test
    void chat_rejectsWhenDisabled() {
        AiAssistantService service = new AiAssistantService(false, chatClientProvider);

        assertThatThrownBy(() -> service.chat("Bonjour"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("désactivé");
    }

    @Test
    void status_enabledRequiresChatClientBean() {
        when(chatClientProvider.getIfAvailable()).thenReturn(null);
        AiAssistantService service = new AiAssistantService(true, chatClientProvider);

        assertThat(service.isEnabled()).isFalse();
    }
}
