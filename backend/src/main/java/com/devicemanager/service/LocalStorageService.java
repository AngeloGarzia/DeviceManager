package com.devicemanager.service;

import com.devicemanager.entity.UploadBlob;
import com.devicemanager.repository.UploadBlobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * Stockage local + copie durable en MySQL (Aiven) pour survivre au disque éphémère Render.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path root;
    private final UploadBlobRepository uploadBlobRepository;

    public LocalStorageService(
            @Value("${app.s3.local-fallback-dir:uploads}") String dir,
            UploadBlobRepository uploadBlobRepository) {
        this.root = resolveWritableRoot(dir);
        this.uploadBlobRepository = uploadBlobRepository;
        log.info("Uploads locaux: dossier={}", root.toAbsolutePath());
    }

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

    @Override
    @Transactional
    public StoredObject store(MultipartFile file) {
        String filename = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            Path target = root.resolve(filename);
            Files.write(target, bytes);

            uploadBlobRepository.save(UploadBlob.builder()
                    .objectKey(filename)
                    .data(bytes)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build());

            return new StoredObject(filename, "/uploads/" + filename, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Échec stockage local", e);
        }
    }

    @Override
    @Transactional
    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Échec suppression locale", e);
        }
        uploadBlobRepository.deleteById(key);
    }

    @Transactional(readOnly = true)
    public Optional<StoredObjectBytes> load(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("/") || key.contains("\\")) {
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
            // fallback DB
        }
        return uploadBlobRepository.findById(key)
                .map(b -> new StoredObjectBytes(b.getData(), b.getContentType(), b.getFileSize()));
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record StoredObjectBytes(byte[] data, String contentType, Long fileSize) {
    }
}
