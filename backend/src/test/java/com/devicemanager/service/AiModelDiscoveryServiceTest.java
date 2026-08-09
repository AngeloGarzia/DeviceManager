package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.dto.AiModelsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelDiscoveryServiceTest {

    @Mock
    private AiApiKeyBattery aiApiKeyBattery;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiModelDiscoveryService aiModelDiscoveryService;

    @Test
    void listModels_withoutApiKey_returnsEmptyWithMessage() {
        when(aiApiKeyBattery.keyFor("openai")).thenReturn("");

        AiModelsResponse response = aiModelDiscoveryService.listModels("openai");

        assertThat(response.isHasApiKey()).isFalse();
        assertThat(response.getModels()).isEmpty();
        assertThat(response.getMessage()).contains("Clé API absente");
        assertThat(response.getProviderId()).isEqualTo("openai");
    }

    @Test
    void firstModelId_withoutApiKey_returnsNull() {
        when(aiApiKeyBattery.keyFor("gemini")).thenReturn(null);

        assertThat(aiModelDiscoveryService.firstModelId("gemini")).isNull();
        assertThat(aiModelDiscoveryService.firstVisionModelId("gemini")).isNull();
    }
}
