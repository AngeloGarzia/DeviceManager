package com.devicemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Stockage local des photos de pièces détachées avec copie durable en MySQL.
 * <p>
 * Active lorsque S3 est désactivé ; survit au disque éphémère Render via
 * la table {@code upload_blob}. Lecture/écriture blob via JDBC.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path root;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructeur : résout un dossier d'uploads accessible en écriture.
     *
     * @param dir chemin préféré (configuration {@code app.s3.local-fallback-dir})
     * @param jdbcTemplate accès JDBC pour la persistance blob
     * @throws IllegalStateException si aucun dossier n'est accessible en écriture
     */
    public LocalStorageService(
            @Value("${app.s3.local-fallback-dir:uploads}") String dir,
            JdbcTemplate jdbcTemplate) {
        this.root = resolveWritableRoot(dir);
        this.jdbcTemplate = jdbcTemplate;
        log.info("Uploads locaux: dossier={}", root.toAbsolutePath());
    }

    /**
     * Retourne le répertoire racine des uploads locaux.
     *
     * @return chemin absolu du dossier de stockage
     */
    public Path getRoot() {
        return root;
    }

    private static Path resolveWritableRoot(String dir) {
        Path[] candidates = new Path[] {
                Paths.get(dir),
                Paths.get("/app/uploads"),
                Paths.get(System.getProperty("java.io.tmpdir"), "device-manager-uploads"),
                Paths.get("/tmp", "device-manager-uploads")
        };
        IOException last = null;
        for (Path candidate : candidates) {
            try {
                Path absolute = candidate.toAbsolutePath().normalize();
                Files.createDirectories(absolute);
                Path probe = absolute.resolve(".write-test");
                Files.writeString(probe, "ok");
                Files.deleteIfExists(probe);
                return absolute;
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw new IllegalStateException(
                "Aucun dossier d'uploads accessible en écriture (vérifier droits Docker / APP_S3_LOCAL_FALLBACK_DIR)",
                last);
    }

    /**
     * Enregistre un fichier sur disque et en base MySQL.
     *
     * @param file fichier à stocker
     * @return clé, URL relative et métadonnées
     */
    @Override
    @Transactional
    public StoredObject store(MultipartFile file) {
        String filename = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            Path target = root.resolve(filename);
            Files.write(target, bytes);
            persistBlob(filename, bytes, file.getContentType(), (long) bytes.length);
            log.info("Upload stocké: key={} bytes={} (disque + MySQL)", filename, bytes.length);
            return new StoredObject(filename, "/uploads/" + filename, file.getContentType(), (long) bytes.length);
        } catch (IOException e) {
            throw new IllegalStateException("Échec stockage local", e);
        }
    }

    /**
     * Supprime un fichier du disque et de la table {@code upload_blob}.
     *
     * @param key clé du fichier (ignorée si invalide)
     */
    @Override
    @Transactional
    public void delete(String key) {
        if (!isSafeKey(key)) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Échec suppression locale", e);
        }
        jdbcTemplate.update("DELETE FROM upload_blob WHERE object_key = ?", key);
    }

    /**
     * Charge un fichier depuis le disque, avec repli et réhydratation depuis MySQL.
     *
     * @param key clé du fichier
     * @return octets et type MIME, ou vide si introuvable ou clé non sûre
     */
    @Transactional(readOnly = true)
    public Optional<StoredObjectBytes> load(String key) {
        if (!isSafeKey(key)) {
            return Optional.empty();
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            return Optional.empty();
        }
        try {
            if (Files.isRegularFile(file)) {
                String contentType = Files.probeContentType(file);
                return Optional.of(new StoredObjectBytes(Files.readAllBytes(file), contentType, Files.size(file)));
            }
        } catch (IOException ignored) {
            // fallback MySQL
        }

        Optional<StoredObjectBytes> fromDb = loadFromDatabase(key);
        fromDb.ifPresent(obj -> rehydrateDisk(key, obj.data()));
        if (fromDb.isEmpty()) {
            log.warn("Upload introuvable (disque + MySQL): {}", key);
        }
        return fromDb;
    }

    private void persistBlob(String key, byte[] data, String contentType, long size) {
        jdbcTemplate.update("""
                INSERT INTO upload_blob (object_key, data, content_type, file_size)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  data = VALUES(data),
                  content_type = VALUES(content_type),
                  file_size = VALUES(file_size)
                """,
                key, data, contentType, size);
    }

    private Optional<StoredObjectBytes> loadFromDatabase(String key) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT data, content_type, file_size FROM upload_blob WHERE object_key = ?",
                    (rs, rowNum) -> {
                        byte[] data = rs.getBytes("data");
                        if (data == null || data.length == 0) {
                            return null;
                        }
                        Long size = rs.getObject("file_size") == null ? (long) data.length : rs.getLong("file_size");
                        return new StoredObjectBytes(data, rs.getString("content_type"), size);
                    },
                    key));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void rehydrateDisk(String key, byte[] data) {
        try {
            Files.write(root.resolve(key), data);
            log.info("Upload rehydraté sur disque depuis MySQL: {}", key);
        } catch (IOException ex) {
            log.warn("Impossible de rehydrater {} sur disque: {}", key, ex.getMessage());
        }
    }

    private static boolean isSafeKey(String key) {
        return key != null
                && !key.isBlank()
                && !key.contains("..")
                && !key.contains("/")
                && !key.contains("\\");
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Contenu binaire d'un fichier chargé depuis le stockage local.
     *
     * @param data octets du fichier
     * @param contentType type MIME
     * @param fileSize taille en octets
     */
    public record StoredObjectBytes(byte[] data, String contentType, Long fileSize) {
        public StoredObjectBytes {
            data = data == null ? null : Arrays.copyOf(data, data.length);
        }

        @Override
        public byte[] data() {
            return data == null ? null : Arrays.copyOf(data, data.length);
        }
    }
}
