package com.devicemanager.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Charge backend/.env.development ou backend/.env.production
 * avant le démarrage Spring (secrets hors frontend).
 */
public final class DotEnvLoader {

    private DotEnvLoader() {}

    public static Map<String, String> load() {
        String profile = resolveProfile();
        String filename = "production".equals(profile) ? ".env.production" : ".env.development";
        Path envFile = resolveEnvFile(filename);
        if (envFile == null) {
            System.err.println("[dotenv] Fichier introuvable: " + filename);
            return Map.of();
        }

        try {
            Map<String, String> values = parseDotEnv(envFile);
            values.forEach((key, value) -> {
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            });
            System.out.println("[dotenv] Chargé: " + envFile.toAbsolutePath()
                    + " (" + values.size() + " variables, profil=" + profile + ")");
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de lire " + envFile, ex);
        }
    }

    private static String resolveProfile() {
        String appEnv = firstNonBlank(System.getenv("APP_ENV"), System.getProperty("APP_ENV"));
        if (appEnv != null) {
            return normalize(appEnv);
        }
        String springProfile = firstNonBlank(
                System.getenv("SPRING_PROFILES_ACTIVE"),
                System.getProperty("spring.profiles.active"));
        if (springProfile != null) {
            return normalize(springProfile.split(",")[0].trim());
        }
        return "development";
    }

    private static String normalize(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("prod") ? "production" : "development";
    }

    private static Path resolveEnvFile(String filename) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd.resolve(filename),
                cwd.resolve("backend").resolve(filename),
                Path.of("c:/Users/dell/device-manager/DeviceManager/backend").resolve(filename)
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, String> parseDotEnv(Path file) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // strip UTF-8 BOM if present on first key
            if (line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
