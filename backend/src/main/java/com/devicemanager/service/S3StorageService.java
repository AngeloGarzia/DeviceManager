package com.devicemanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * Stockage S3 des photos de pièces détachées DeviceManager.
 * <p>
 * Active lorsque {@code app.s3.enabled=true} ; remplace le stockage local
 * pour les déploiements cloud multi-instances.
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    /**
     * @param s3Client client AWS SDK configuré
     * @param bucket nom du bucket S3
     * @param region région AWS du bucket
     */
    public S3StorageService(
            S3Client s3Client,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    /**
     * Upload un fichier vers S3 sous le préfixe {@code spare-parts/}.
     *
     * @param file fichier multipart
     * @return clé S3, URL HTTPS publique et métadonnées
     * @throws IllegalStateException en cas d'échec d'upload
     */
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
            String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
            return new StoredObject(key, url, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Échec upload S3", e);
        }
    }

    /**
     * Supprime un objet du bucket S3.
     *
     * @param key clé S3 de l'objet
     */
    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "photo.jpg";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
