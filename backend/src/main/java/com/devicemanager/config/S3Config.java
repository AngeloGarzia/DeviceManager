package com.devicemanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Client / présigneur object storage S3-compatible — cible : <strong>Cloudflare R2</strong> (bucket privé).
 * <p>
 * Activé si {@code app.s3.enabled=true}. Endpoint custom → path-style (R2).
 */
@Configuration
public class S3Config {

    @Bean
    @ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
    public AwsCredentialsProvider s3CredentialsProvider(
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    @ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
    public S3Client s3Client(
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.endpoint:}") String endpoint) {

        Region awsRegion = Region.of(blankToAuto(region));
        var builder = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(trimTrailingSlash(endpoint)))
                    .serviceConfiguration(r2ServiceConfiguration());
        }
        return builder.build();
    }

    /**
     * Présigneur GET pour URLs temporaires (bucket R2 privé).
     * Même endpoint / path-style que {@link S3Client}.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
    public S3Presigner s3Presigner(
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.endpoint:}") String endpoint) {

        Region awsRegion = Region.of(blankToAuto(region));
        var builder = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(trimTrailingSlash(endpoint)))
                    .serviceConfiguration(r2ServiceConfiguration());
        }
        return builder.build();
    }

    /**
     * Path-style + checksums désactivés : requis pour R2 et pour des URLs GET
     * exécutables par le navigateur (sans en-tête checksum signé).
     */
    private static S3Configuration r2ServiceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .checksumValidationEnabled(false)
                .chunkedEncodingEnabled(false)
                .build();
    }

    private static String blankToAuto(String region) {
        return region == null || region.isBlank() ? "auto" : region;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
