package com.devicemanager.service;

import com.devicemanager.entity.UploadBlob;
import com.devicemanager.repository.UploadBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private UploadBlobRepository uploadBlobRepository;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(tempDir.toString(), uploadBlobRepository);
    }

    @Test
    void store_writesFileAndPersistsBlob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "carte mère.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        StorageService.StoredObject stored = storage.store(file);

        assertThat(stored.key()).contains("carte_m_re.jpg");
        assertThat(stored.url()).startsWith("/uploads/");
        assertThat(Files.exists(tempDir.resolve(stored.key()))).isTrue();

        ArgumentCaptor<UploadBlob> captor = ArgumentCaptor.forClass(UploadBlob.class);
        verify(uploadBlobRepository).save(captor.capture());
        assertThat(captor.getValue().getObjectKey()).isEqualTo(stored.key());
        assertThat(captor.getValue().getData()).containsExactly(1, 2, 3);
    }

    @Test
    void store_usesDefaultNameWhenBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "", MediaType.IMAGE_JPEG_VALUE, new byte[]{9});

        StorageService.StoredObject stored = storage.store(file);

        assertThat(stored.key()).endsWith("-photo.jpg");
        verify(uploadBlobRepository).save(any(UploadBlob.class));
    }

    @Test
    void delete_removesFileAndBlob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "x.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});
        StorageService.StoredObject stored = storage.store(file);

        storage.delete(stored.key());

        assertThat(Files.exists(tempDir.resolve(stored.key()))).isFalse();
        verify(uploadBlobRepository).deleteById(stored.key());
    }

    @Test
    void load_returnsDiskFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "y.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{7, 8});
        StorageService.StoredObject stored = storage.store(file);

        var loaded = storage.load(stored.key());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().data()).containsExactly(7, 8);
    }

    @Test
    void load_fallsBackToDatabase() {
        when(uploadBlobRepository.findById("db-only.jpg")).thenReturn(Optional.of(
                UploadBlob.builder()
                        .objectKey("db-only.jpg")
                        .data(new byte[]{4, 5, 6})
                        .contentType("image/jpeg")
                        .fileSize(3L)
                        .build()));

        var loaded = storage.load("db-only.jpg");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().data()).containsExactly(4, 5, 6);
        assertThat(loaded.get().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void load_rejectsPathTraversal() {
        assertThat(storage.load("../secret.jpg")).isEmpty();
        assertThat(storage.load("a/b.jpg")).isEmpty();
    }
}
