package com.devicemanager.service;

import com.devicemanager.entity.AppSetting;
import com.devicemanager.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsServiceTest.class);

    @Mock private AppSettingRepository appSettingRepository;
    @Mock private com.devicemanager.ai.AiApiKeyBattery aiApiKeyBattery;
    @InjectMocks private AppSettingsService appSettingsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(appSettingsService, "defaultMailEnabled", "false");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailFrom", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailAdminEmail", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailHost", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailPort", "587");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailUsername", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultMailPassword", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultJwtExpirationMs", "86400000");
        ReflectionTestUtils.setField(appSettingsService, "defaultCorsOrigins", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultS3Enabled", "false");
        ReflectionTestUtils.setField(appSettingsService, "defaultS3Bucket", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultS3Region", "");
        ReflectionTestUtils.setField(appSettingsService, "defaultLocalUploadDir", "uploads");
        ReflectionTestUtils.setField(appSettingsService, "defaultAiEnabled", "false");
        ReflectionTestUtils.setField(appSettingsService, "defaultAiProvider", "openai");
        ReflectionTestUtils.setField(appSettingsService, "defaultAiModel", "");
        ReflectionTestUtils.setField(appSettingsService, "legacyOpenAiChatModel", "gpt-4o-mini");
    }

    @Test
    void getBoolean_parsesTruthyValues() {
        log.info("Test AppSettings getBoolean");
        when(appSettingRepository.findAll()).thenReturn(List.of(
                AppSetting.builder().settingKey(AppSettingsService.MAIL_ENABLED).settingValue("yes").build()
        ));
        ReflectionTestUtils.invokeMethod(appSettingsService, "reloadCache");

        assertThat(appSettingsService.getBoolean(AppSettingsService.MAIL_ENABLED, false)).isTrue();
        assertThat(appSettingsService.get("MISSING", "fallback")).isEqualTo("fallback");
        assertThat(appSettingsService.getLong(AppSettingsService.JWT_EXPIRATION_MS, 1L)).isEqualTo(1L);
    }

    @Test
    void list_masksSecrets() {
        when(appSettingRepository.findAllByOrderByCategoryAscLabelAsc()).thenReturn(List.of(
                AppSetting.builder()
                        .settingKey(AppSettingsService.MAIL_PASSWORD)
                        .settingValue("secret")
                        .label("Mot de passe")
                        .category("Messagerie")
                        .secretValue(true)
                        .build()
        ));

        var list = appSettingsService.list();

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getValue()).isEqualTo("********");
        assertThat(list.getFirst().isSecret()).isTrue();
    }
}
