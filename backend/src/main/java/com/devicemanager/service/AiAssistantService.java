package com.devicemanager.service;

import com.devicemanager.dto.AiChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class AiAssistantService {

    private final boolean enabled;
    private final ObjectProvider<ChatClient> chatClientProvider;

    public AiAssistantService(
            @Value("${app.ai.enabled:false}") boolean enabled,
            ObjectProvider<ChatClient> chatClientProvider) {
        this.enabled = enabled;
        this.chatClientProvider = chatClientProvider;
    }

    public boolean isEnabled() {
        return enabled && chatClientProvider.getIfAvailable() != null;
    }

    public AiChatResponse status() {
        return AiChatResponse.builder()
                .enabled(isEnabled())
                .reply(isEnabled()
                        ? "Assistant IA prêt."
                        : "Assistant IA désactivé. Définir APP_AI_ENABLED=true, SPRING_AI_MODEL_CHAT=openai et OPENAI_API_KEY.")
                .build();
    }

    public AiChatResponse chat(String message) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assistant IA désactivé (APP_AI_ENABLED / OPENAI_API_KEY)");
        }
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ChatClient Spring AI indisponible (SPRING_AI_MODEL_CHAT=openai ?)");
        }
        try {
            String reply = chatClient.prompt()
                    .user(message.trim())
                    .call()
                    .content();
            return AiChatResponse.builder()
                    .enabled(true)
                    .reply(reply == null || reply.isBlank() ? "(réponse vide)" : reply.trim())
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec appel Spring AI: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Échec de l'appel au modèle IA: " + ex.getMessage());
        }
    }
}
