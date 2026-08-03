package com.devicemanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(tempDir.toString(), jdbcTemplate);
    }

    @Test
    void store_writesFileAndPersistsBlob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "carte mère.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        StorageService.StoredObject stored = storage.store(file);

        assertThat(stored.key()).contains("carte_m_re.jpg");
        assertThat(stored.url()).startsWith("/uploads/");
        assertThat(Files.exists(tempDir.resolve(stored.key()))).isTrue();
        verify(jdbcTemplate).update(anyString(), eq(stored.key()), ArgumentMatchers.<byte[]>any(),
                eq(MediaType.IMAGE_JPEG_VALUE), eq(3L));
    }

    @Test
    void store_usesDefaultNameWhenBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "photos", "", MediaType.IMAGE_JPEG_VALUE, new byte[]{9});

        StorageService.StoredObject stored = storage.store(file);

        assertThat(stored.key()).endsWith("-photo.jpg");
        verify(jdbcTemplate).update(anyString(), eq(stored.key()), ArgumentMatchers.<byte[]>any(),
                eq(MediaType.IMAGE_JPEG_VALUE), eq(1L));
    }

    @Test
    void delete_removesFileAndBlob() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile(
                "photos", "x.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});
        StorageService.StoredObject stored = storage.store(file);

        storage.delete(stored.key());

        assertThat(Files.exists(tempDir.resolve(stored.key()))).isFalse();
        verify(jdbcTemplate).update(eq("DELETE FROM upload_blob WHERE object_key = ?"), eq(stored.key()));
    }

    @Test
    void load_returnsDiskFile() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile(
                "photos", "y.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{7, 8});
        StorageService.StoredObject stored = storage.store(file);

        var loaded = storage.load(stored.key());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().data()).containsExactly(7, 8);
    }

    @Test
    void load_fallsBackToDatabase() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                ArgumentMatchers.<RowMapper<LocalStorageService.StoredObjectBytes>>any(),
                eq("db-only.jpg")))
                .thenReturn(new LocalStorageService.StoredObjectBytes(
                        new byte[]{4, 5, 6}, "image/jpeg", 3L));

        var loaded = storage.load("db-only.jpg");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().data()).containsExactly(4, 5, 6);
        assertThat(loaded.get().contentType()).isEqualTo("image/jpeg");
        assertThat(Files.exists(tempDir.resolve("db-only.jpg"))).isTrue();
    }

    @Test
    void load_returnsEmptyWhenMissingEverywhere() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                ArgumentMatchers.<RowMapper<LocalStorageService.StoredObjectBytes>>any(),
                eq("missing.jpg")))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(storage.load("missing.jpg")).isEmpty();
    }

    @Test
    void load_rejectsPathTraversal() {
        assertThat(storage.load("../secret.jpg")).isEmpty();
        assertThat(storage.load("a/b.jpg")).isEmpty();
    }
}
