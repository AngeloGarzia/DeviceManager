package com.devicemanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stockage local de secours quand S3 est désactivé.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${app.s3.local-fallback-dir:uploads}") String dir) {
        this.root = resolveWritableRoot(dir);
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

    @Override
    public StoredObject store(MultipartFile file) {
        String filename = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        try {
            Path target = root.resolve(filename);
            Files.copy(file.getInputStream(), target);
            return new StoredObject(filename, "/uploads/" + filename, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Échec stockage local", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Échec suppression locale", e);
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
