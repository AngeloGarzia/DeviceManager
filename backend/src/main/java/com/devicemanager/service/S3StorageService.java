package com.devicemanager.service;

import com.devicemanager.exception.StorageUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Stockage objet S3-compatible (Cloudflare R2) — bucket <strong>privé</strong>.
 * <p>
 * Les accès lecture passent par des URLs présignées GET ({@link #resolveAccessUrl}).
 * Aucune URL présignée n'est écrite en base : uniquement la clé objet.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final JdbcTemplate jdbcTemplate;
    private final String bucket;
    private final Duration mediaExpiration;
    private final Duration documentExpiration;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            JdbcTemplate jdbcTemplate,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.presigned-url-expiration-minutes:15}") long mediaExpirationMinutes,
            @Value("${app.s3.presigned-document-expiration-minutes:60}") long documentExpirationMinutes) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "APP_S3_BUCKET est obligatoire lorsque APP_S3_ENABLED=true");
        }
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.jdbcTemplate = jdbcTemplate;
        this.bucket = bucket.trim();
        this.mediaExpiration = Duration.ofMinutes(Math.max(1, mediaExpirationMinutes));
        this.documentExpiration = Duration.ofMinutes(Math.max(1, documentExpirationMinutes));
    }

    @Override
    public StoredObject store(MultipartFile file) {
        String key = "spare-parts/" + UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            // url = clé stable uniquement (pas d'URL présignée / publique permanente)
            return new StoredObject(key, key, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Échec d'enregistrement du fichier. Réessayez.", e);
        } catch (S3Exception e) {
            log.error("Échec PutObject R2 (bucket={}, key={}, http={}): {}",
                    bucket, key, e.statusCode(), e.getMessage(), e);
            throw new StorageUnavailableException(
                    "Stockage cloud indisponible : impossible d'enregistrer le fichier. Réessayez dans un instant.", e);
        } catch (RuntimeException e) {
            log.error("Échec PutObject R2 (bucket={}, key={}): {}", bucket, key, e.getMessage(), e);
            throw new StorageUnavailableException(
                    "Stockage cloud indisponible : impossible d'enregistrer le fichier. Réessayez dans un instant.", e);
        }
    }

    @Override
    public void delete(String key) {
        String objectKey = normalizeObjectKey(key);
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException ex) {
            log.warn("Échec DeleteObject R2 (key={}): {}", objectKey, ex.getMessage());
        }
        try {
            jdbcTemplate.update("DELETE FROM upload_blob WHERE object_key = ?", objectKey);
        } catch (RuntimeException ignored) {
            // table absente ou clé locale différente — non bloquant
        }
    }

    @Override
    public Optional<StoredObjectBytes> load(String key) {
        String objectKey = normalizeObjectKey(key);
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        Optional<StoredObjectBytes> fromR2 = loadFromR2(objectKey);
        if (fromR2.isPresent()) {
            return fromR2;
        }
        // Fichiers uploadés en mode local (avant R2) : repli MySQL upload_blob.
        Optional<StoredObjectBytes> fromBlob = loadFromUploadBlob(objectKey);
        if (fromBlob.isPresent()) {
            log.info("Lecture R2 manquante — fichier servi depuis upload_blob: {}", objectKey);
            return fromBlob;
        }
        log.warn("Objet introuvable (R2 + upload_blob): {}", objectKey);
        return Optional.empty();
    }

    @Override
    public String resolveAccessUrl(String objectKey, AccessKind kind) {
        String key = normalizeObjectKey(objectKey);
        if (key == null || key.isBlank()) {
            return null;
        }
        Duration ttl = kind == AccessKind.DOCUMENT ? documentExpiration : mediaExpiration;
        try {
            return generatePresignedUrl(key, ttl);
        } catch (RuntimeException ex) {
            // Ne jamais faire échouer un GET métier (ex. /api/devices/{id}) pour une URL média.
            log.error("Échec URL présignée R2 (bucket={}, key={}): {}", bucket, key, ex.getMessage());
            return null;
        }
    }

    /**
     * Génère une URL présignée GET temporaire pour un objet du bucket.
     */
    public String generatePresignedUrl(String objectKey, Duration expiration) {
        String key = normalizeObjectKey(objectKey);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("objectKey invalide");
        }
        Duration ttl = expiration == null || expiration.isNegative() || expiration.isZero()
                ? mediaExpiration
                : expiration;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * Normalise une clé stockée (retire préfixe bucket /uploads, extrait depuis URL).
     */
    String normalizeObjectKey(String raw) {
        String key = StorageService.extractObjectKey(raw);
        if (key == null || key.isBlank()) {
            return null;
        }
        String prefix = bucket + "/";
        if (key.startsWith(prefix)) {
            key = key.substring(prefix.length());
        }
        if (key.startsWith("uploads/")) {
            key = key.substring("uploads/".length());
        }
        return key.isBlank() ? null : key;
    }

    private Optional<StoredObjectBytes> loadFromR2(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            byte[] data = response.asByteArray();
            if (data == null || data.length == 0) {
                return Optional.empty();
            }
            GetObjectResponse meta = response.response();
            String contentType = meta != null ? meta.contentType() : null;
            Long size = meta != null && meta.contentLength() != null
                    ? meta.contentLength()
                    : (long) data.length;
            return Optional.of(new StoredObjectBytes(data, contentType, size));
        } catch (NoSuchKeyException ex) {
            // Fichier réellement absent → cas métier (le caller fera 404 / repli DB).
            return Optional.empty();
        } catch (S3Exception ex) {
            int status = ex.statusCode();
            if (status == 404) {
                // NoSuchBucket ou clé absente non typée : reste un cas métier.
                return Optional.empty();
            }
            // 401/403/5xx et co. : ne pas masquer en 404 métier — remonter 503.
            log.error("Erreur R2 (key={}, http={}): {}", objectKey, status, ex.getMessage(), ex);
            throw new StorageUnavailableException(
                    "Stockage cloud indisponible ou refusé (code " + status
                            + "). Réessayez ou vérifiez la configuration R2.", ex);
        } catch (RuntimeException ex) {
            // SDK / réseau / DNS / timeout : pas un « fichier introuvable ».
            log.error("Échec lecture R2 (key={}): {}", objectKey, ex.getMessage(), ex);
            throw new StorageUnavailableException(
                    "Stockage cloud injoignable. Réessayez dans un instant.", ex);
        }
    }

    private Optional<StoredObjectBytes> loadFromUploadBlob(String objectKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT data, content_type, file_size FROM upload_blob WHERE object_key = ?",
                    (rs, rowNum) -> {
                        byte[] data = rs.getBytes("data");
                        if (data == null || data.length == 0) {
                            return null;
                        }
                        Long size = rs.getObject("file_size") == null
                                ? (long) data.length
                                : rs.getLong("file_size");
                        return new StoredObjectBytes(data, rs.getString("content_type"), size);
                    },
                    objectKey));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Échec lecture upload_blob (key={}): {}", objectKey, ex.getMessage());
            return Optional.empty();
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
