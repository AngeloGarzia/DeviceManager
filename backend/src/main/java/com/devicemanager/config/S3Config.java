package com.devicemanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Configuration du client Amazon S3 (ou compatible S3) pour le stockage d'objets.
 * <p>
 * Activé uniquement si {@code app.s3.enabled=true}. Un endpoint personnalisé active
 * l'accès path-style (MinIO, Scaleway, etc.).
 */
@Configuration
public class S3Config {

    /**
     * Construit un {@link S3Client} à partir des propriétés {@code app.s3.*}.
     *
     * @param region    région AWS (ex. {@code eu-west-3})
     * @param accessKey identifiant d'accès
     * @param secretKey clé secrète
     * @param endpoint  URL de endpoint optionnelle (vide = AWS standard)
     * @return client S3 configuré
     */
    @Bean
    @ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
    public S3Client s3Client(
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey,
            @Value("${app.s3.endpoint:}") String endpoint) {

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return builder.build();
    }
}
