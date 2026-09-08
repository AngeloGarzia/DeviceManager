package com.devicemanager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
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
    private PresignedGetObjectRequest presignedGetObjectRequest;

    @Test
    void generatePresignedUrl_usesConfiguredTtlAndBucket() throws Exception {
        when(presignedGetObjectRequest.url()).thenReturn(new URL("https://example.r2.cloudflarestorage.com/devicemanager/spare-parts/a.jpg?X-Amz-Signature=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);

        S3StorageService service = new S3StorageService(s3Client, s3Presigner, "devicemanager", 15, 60);
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

        S3StorageService service = new S3StorageService(s3Client, s3Presigner, "devicemanager", 15, 60);
        service.resolveAccessUrl("spare-parts/doc.pdf", StorageService.AccessKind.DOCUMENT);

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void resolveAccessUrl_returnsNullWhenPresignFails() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("Bucket cannot be empty"));

        S3StorageService service = new S3StorageService(s3Client, s3Presigner, "devicemanager", 15, 60);
        assertThat(service.resolveAccessUrl("spare-parts/a.jpg", StorageService.AccessKind.MEDIA)).isNull();
    }

    @Test
    void extractObjectKey_fromR2PathStyleUrl() {
        String key = StorageService.extractObjectKey(
                "https://abc.r2.cloudflarestorage.com/devicemanager/spare-parts/photo.jpg");
        assertThat(key).isEqualTo("spare-parts/photo.jpg");
    }

    private static URL URI_CREATE(String value) {
        try {
            return new URL(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
