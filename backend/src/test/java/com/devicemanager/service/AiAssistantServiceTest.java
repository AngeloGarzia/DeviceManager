package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private AppSettingsService appSettingsService;
    @Mock
    private AiApiKeyBattery aiApiKeyBattery;
    @Mock
    private AiModelDiscoveryService aiModelDiscoveryService;
    @Mock
    private ImageOptimizationService imageOptimizationService;
    @Mock
    private WebEnrichmentService webEnrichmentService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    @Test
    void status_reportsDisabledWhenFlagOff() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(false);
        when(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai")).thenReturn("openai");
        when(appSettingsService.get(AppSettingsService.AI_MODEL, "")).thenReturn("gpt-4o-mini");

        assertThat(aiAssistantService.isEnabled()).isFalse();
        assertThat(aiAssistantService.status().isEnabled()).isFalse();
        assertThat(aiAssistantService.status().getReply()).contains("paramètres");
    }

    @Test
    void chat_rejectsWhenDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(false);

        assertThatThrownBy(() -> aiAssistantService.chat("Bonjour"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("désactivé");
    }

    @Test
    void chat_rejectsWhenApiKeyMissing() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai")).thenReturn("openai");
        when(aiApiKeyBattery.keyFor(anyString())).thenReturn("");

        assertThatThrownBy(() -> aiAssistantService.chat("Bonjour"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Clé API");
    }

    @Test
    void status_enabledWhenFlagAndKeyPresent() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai")).thenReturn("openai");
        when(aiApiKeyBattery.keyFor("openai")).thenReturn("sk-test");
        when(appSettingsService.get(AppSettingsService.AI_MODEL, "")).thenReturn("gpt-4o-mini");

        assertThat(aiAssistantService.isEnabled()).isTrue();
        assertThat(aiAssistantService.status().getReply()).contains("OpenAI");
        assertThat(aiAssistantService.status().getReply()).contains("gpt-4o-mini");
    }

    @Test
    void status_enabledWhenEnvBatteryKeyPresent() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(true);
        when(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai")).thenReturn("gemini");
        when(aiApiKeyBattery.keyFor("gemini")).thenReturn("gem-test");
        when(appSettingsService.get(AppSettingsService.AI_MODEL, "")).thenReturn("gemini-3.1-flash-lite");

        assertThat(aiAssistantService.isEnabled()).isTrue();
        assertThat(aiAssistantService.status().getReply()).contains("Gemini");
    }

    @Test
    void scanLabel_rejectsWhenDisabled() {
        when(appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)).thenReturn(false);
        MockMultipartFile image = new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{1, 2});

        assertThatThrownBy(() -> aiAssistantService.scanLabel(image))
                .isInstanceOf(ResponseStatusException.class);
    }
}
