package com.devicemanager.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@ConditionalOnBean(ChatClient.Builder.class)
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            Tu es l'assistant DeviceManager, une application de gestion de pièces détachées
            pour machines à sous (casinos / ateliers techniques).

            Tu aides les administrateurs et techniciens à :
            - comprendre le catalogue pièces, MAS, SFM et demandes de commande ;
            - rédiger un message de demande de commande clair ;
            - expliquer les statuts (PENDING / VALIDATED) et le flux de validation ;
            - proposer des bonnes pratiques (références, photos, contacts SFM).

            Réponds en français, de façon concise et professionnelle.
            Si tu manques d'information métier précise (stock réel, IDs), dis-le clairement
            plutôt que d'inventer.
            """;

    @Bean
    ChatClient deviceManagerChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
