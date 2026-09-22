package com.devicemanager.service;

import com.devicemanager.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3StorageService newService() {
        return new S3StorageService(s3Client, s3Presigner, jdbcTemplate, "devicemanager", 15, 60);
    }

    @Test
    void generatePresignedUrl_usesConfiguredTtlAndBucket() throws Exception {
        when(presignedGetObjectRequest.url()).thenReturn(new URL("https://example.r2.cloudflarestorage.com/devicemanager/spare-parts/a.jpg?X-Amz-Signature=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);

        S3StorageService service = newService();
        String url = service.generatePresignedUrl("spare-parts/a.jpg", Duration.ofMinutes(15));

        assertThat(url).contains("X-Amz-Signature=abc");

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("devicemanager");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("spare-parts/a.jpg");
    }

    @Test
    void resolveAccessUrl_documentUsesLongerDefault() {
        when(presignedGetObjectRequest.url()).thenReturn(URI_CREATE("https://r2.example/obj?sig=1"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);

        S3StorageService service = newService();
        service.resolveAccessUrl("spare-parts/doc.pdf", StorageService.AccessKind.DOCUMENT);

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void resolveAccessUrl_returnsNullWhenPresignFails() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("Bucket cannot be empty"));

        S3StorageService service = newService();
        assertThat(service.resolveAccessUrl("spare-parts/a.jpg", StorageService.AccessKind.MEDIA)).isNull();
    }

    @Test
    void normalizeObjectKey_stripsBucketPrefix() {
        S3StorageService service = newService();
        assertThat(service.normalizeObjectKey("devicemanager/spare-parts/a.pdf"))
                .isEqualTo("spare-parts/a.pdf");
        assertThat(service.normalizeObjectKey("/uploads/old-local.pdf"))
                .isEqualTo("old-local.pdf");
    }

    @Test
    void extractObjectKey_fromR2PathStyleUrl() {
        String key = StorageService.extractObjectKey(
                "https://abc.r2.cloudflarestorage.com/devicemanager/spare-parts/photo.jpg");
        assertThat(key).isEqualTo("spare-parts/photo.jpg");
    }

    @Test
    void load_returnsEmptyOnNoSuchKey() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

        S3StorageService service = newService();
        assertThat(service.load("spare-parts/absent.pdf")).isEmpty();
    }

    @Test
    void load_throwsStorageUnavailableOnAuthError() {
        S3Exception forbidden = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                .message("Access Denied")
                .build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(forbidden);

        S3StorageService service = newService();
        assertThatThrownBy(() -> service.load("spare-parts/x.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("403");
    }

    @Test
    void load_throwsStorageUnavailableOnServerError() {
        S3Exception serverError = (S3Exception) S3Exception.builder()
                .statusCode(503)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("SlowDown").build())
                .message("Slow down")
                .build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(serverError);

        S3StorageService service = newService();
        assertThatThrownBy(() -> service.load("spare-parts/x.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("503");
    }

    @Test
    void load_throwsStorageUnavailableOnSdkClientError() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("Unable to connect").build());

        S3StorageService service = newService();
        assertThatThrownBy(() -> service.load("spare-parts/x.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("injoignable");
    }

    @Test
    void store_wrapsS3ExceptionAsStorageUnavailable() {
        S3Exception serverError = (S3Exception) S3Exception.builder()
                .statusCode(500)
                .message("Internal Error")
                .build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(serverError);

        S3StorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("Stockage cloud indisponible");
    }

    private static URL URI_CREATE(String value) {
        try {
            return new URL(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
