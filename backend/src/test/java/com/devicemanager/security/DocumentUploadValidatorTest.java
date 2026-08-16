package com.devicemanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUploadValidatorTest {

    private static final byte[] PDF = "%PDF-1.4".getBytes();
    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 0, 0, 0, 0, 0, 0, 0};

    @Test
    void acceptsPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", PDF);
        assertThat(DocumentUploadValidator.validatePdfOrImage(file, "document"))
                .isEqualTo(DocumentUploadValidator.Kind.PDF);
    }

    @Test
    void acceptsJpegImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.jpg", "image/jpeg", JPEG);
        assertThat(DocumentUploadValidator.validatePdfOrImage(file, "document"))
                .isEqualTo(DocumentUploadValidator.Kind.IMAGE);
    }

    @Test
    void rejectsOtherTypes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> DocumentUploadValidator.validatePdfOrImage(file, "devis"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
