package com.devicemanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Stockage objet S3-compatible (Cloudflare R2) — bucket <strong>privé</strong>.
 * <p>
 * Les accès lecture passent par des URLs présignées GET ({@link #resolveAccessUrl}).
 * Aucune URL présignée n'est écrite en base : uniquement la clé objet.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration mediaExpiration;
    private final Duration documentExpiration;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.presigned-url-expiration-minutes:15}") long mediaExpirationMinutes,
            @Value("${app.s3.presigned-document-expiration-minutes:60}") long documentExpirationMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
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
            throw new IllegalStateException("Échec d'enregistrement de la photo. Réessayez.", e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String objectKey = StorageService.extractObjectKey(key);
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    @Override
    public String resolveAccessUrl(String objectKey, AccessKind kind) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String key = StorageService.extractObjectKey(objectKey);
        if (key == null || key.isBlank()) {
            return null;
        }
        Duration ttl = kind == AccessKind.DOCUMENT ? documentExpiration : mediaExpiration;
        return generatePresignedUrl(key, ttl);
    }

    /**
     * Génère une URL présignée GET temporaire pour un objet du bucket.
     */
    public String generatePresignedUrl(String objectKey, Duration expiration) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey requis");
        }
        String key = StorageService.extractObjectKey(objectKey);
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

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
