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
 * Stockage local de secours quand S3 est désactivé (dev).
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${app.s3.local-fallback-dir:uploads}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
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
