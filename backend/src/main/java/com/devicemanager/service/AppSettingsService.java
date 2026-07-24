package com.devicemanager.service;

import com.devicemanager.dto.AppSettingResponse;
import com.devicemanager.dto.AppSettingsUpdateRequest;
import com.devicemanager.entity.AppSetting;
import com.devicemanager.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional
public class AppSettingsService {

    public static final String MAIL_ENABLED = "MAIL_ENABLED";
    public static final String MAIL_FROM = "MAIL_FROM";
    public static final String MAIL_ADMIN_EMAIL = "MAIL_ADMIN_EMAIL";
    public static final String MAIL_HOST = "MAIL_HOST";
    public static final String MAIL_PORT = "MAIL_PORT";
    public static final String MAIL_USERNAME = "MAIL_USERNAME";
    public static final String MAIL_PASSWORD = "MAIL_PASSWORD";
    public static final String JWT_EXPIRATION_MS = "JWT_EXPIRATION_MS";
    public static final String CORS_ALLOWED_ORIGINS = "CORS_ALLOWED_ORIGINS";
    public static final String S3_ENABLED = "S3_ENABLED";
    public static final String S3_BUCKET = "S3_BUCKET";
    public static final String S3_REGION = "S3_REGION";
    public static final String LOCAL_UPLOAD_DIR = "LOCAL_UPLOAD_DIR";

    private final AppSettingRepository appSettingRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Value("${app.mail.enabled:false}")
    private String defaultMailEnabled;

    @Value("${app.mail.from:}")
    private String defaultMailFrom;

    @Value("${app.mail.admin-email:}")
    private String defaultMailAdminEmail;

    @Value("${spring.mail.host:}")
    private String defaultMailHost;

    @Value("${spring.mail.port:587}")
    private String defaultMailPort;

    @Value("${spring.mail.username:}")
    private String defaultMailUsername;

    @Value("${spring.mail.password:}")
    private String defaultMailPassword;

    @Value("${app.jwt.expiration-ms:86400000}")
    private String defaultJwtExpirationMs;

    @Value("${app.cors.allowed-origins:}")
    private String defaultCorsOrigins;

    @Value("${app.s3.enabled:false}")
    private String defaultS3Enabled;

    @Value("${app.s3.bucket:}")
    private String defaultS3Bucket;

    @Value("${app.s3.region:}")
    private String defaultS3Region;

    @Value("${app.s3.local-fallback-dir:uploads}")
    private String defaultLocalUploadDir;

    @PostConstruct
    public void initDefaults() {
        ensure(MAIL_ENABLED, defaultMailEnabled, "Activer l'envoi d'emails", "Messagerie", false);
        ensure(MAIL_FROM, defaultMailFrom, "Email expéditeur", "Messagerie", false);
        ensure(MAIL_ADMIN_EMAIL, defaultMailAdminEmail, "Email administrateur (destinataire)", "Messagerie", false);
        ensure(MAIL_HOST, defaultMailHost, "Serveur SMTP (hôte)", "Messagerie", false);
        ensure(MAIL_PORT, defaultMailPort, "Port SMTP", "Messagerie", false);
        ensure(MAIL_USERNAME, defaultMailUsername, "Identifiant SMTP", "Messagerie", false);
        ensure(MAIL_PASSWORD, defaultMailPassword, "Mot de passe SMTP", "Messagerie", true);
        ensure(JWT_EXPIRATION_MS, defaultJwtExpirationMs, "Durée du jeton JWT (ms)", "Sécurité", false);
        ensure(CORS_ALLOWED_ORIGINS, defaultCorsOrigins, "Origines CORS autorisées", "Sécurité", false);
        ensure(S3_ENABLED, defaultS3Enabled, "Activer le stockage S3", "Stockage", false);
        ensure(S3_BUCKET, defaultS3Bucket, "Bucket S3", "Stockage", false);
        ensure(S3_REGION, defaultS3Region, "Région S3", "Stockage", false);
        ensure(LOCAL_UPLOAD_DIR, defaultLocalUploadDir, "Dossier local des uploads", "Stockage", false);
        reloadCache();
    }

    @Transactional(readOnly = true)
    public List<AppSettingResponse> list() {
        return appSettingRepository.findAllByOrderByCategoryAscLabelAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AppSettingResponse> update(AppSettingsUpdateRequest request) {
        Map<String, String> values = request.getValues() == null ? Map.of() : request.getValues();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            AppSetting setting = appSettingRepository.findById(entry.getKey()).orElse(null);
            if (setting == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (setting.isSecretValue() && (value.isBlank() || "********".equals(value))) {
                continue;
            }
            setting.setSettingValue(value);
            appSettingRepository.save(setting);
        }
        reloadCache();
        return list();
    }

    public String get(String key, String fallback) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    public long getLong(String key, long fallback) {
        try {
            return Long.parseLong(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void ensure(String key, String value, String label, String category, boolean secret) {
        if (appSettingRepository.existsById(key)) {
            return;
        }
        appSettingRepository.save(AppSetting.builder()
                .settingKey(key)
                .settingValue(value == null ? "" : value)
                .label(label)
                .category(category)
                .secretValue(secret)
                .build());
    }

    private void reloadCache() {
        Map<String, String> next = new HashMap<>();
        for (AppSetting setting : appSettingRepository.findAll()) {
            next.put(setting.getSettingKey(), setting.getSettingValue() == null ? "" : setting.getSettingValue());
        }
        cache.clear();
        cache.putAll(next);
    }

    private AppSettingResponse toResponse(AppSetting setting) {
        String value = setting.getSettingValue() == null ? "" : setting.getSettingValue();
        if (setting.isSecretValue() && !value.isBlank()) {
            value = "********";
        }
        return AppSettingResponse.builder()
                .key(setting.getSettingKey())
                .value(value)
                .label(setting.getLabel())
                .category(setting.getCategory())
                .secret(setting.isSecretValue())
                .build();
    }
}
