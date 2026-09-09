package com.devicemanager.security;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepFileContentValidatorTest {

    /** JPEG 1×1 minimal (Tika + magic bytes). */
    private static final byte[] JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00,
            0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14,
            0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A,
            0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C,
            0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00,
            (byte) 0xFF, (byte) 0xC4, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08,
            (byte) 0xFF, (byte) 0xC4, 0x00, 0x14, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, (byte) 0xFF,
            (byte) 0xD9
    };

    @Test
    void acceptsLegitimateJpeg() {
        FileMagicBytesValidator.validateImageMagicBytes(JPEG);
        assertThatCode(() -> DeepFileContentValidator.validateImage(JPEG, "image/jpeg"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsLegitimatePdf() throws Exception {
        byte[] pdf = minimalPdf(false);
        FileMagicBytesValidator.validatePdfMagicBytes(pdf);
        assertThatCode(() -> DeepFileContentValidator.validatePdf(pdf, "application/pdf"))
                .doesNotThrowAnyException();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf);
        assertThat(DocumentUploadValidator.validatePdfOrImage(file, "document"))
                .isEqualTo(DocumentUploadValidator.Kind.PDF);
    }

    @Test
    void rejectsPdfWithEmbeddedJavaScript() throws Exception {
        byte[] pdf = minimalPdf(true);
        FileMagicBytesValidator.validatePdfMagicBytes(pdf);
        assertThatThrownBy(() -> DeepFileContentValidator.validatePdf(pdf, "application/pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).containsIgnoringCase("script");
                });
    }

    @Test
    void rejectsPolyglotMimeMismatch() {
        // Contenu JPEG annoncé comme PDF : magic PDF échoue (fail-fast) via DocumentUploadValidator.
        MockMultipartFile asPdf = new MockMultipartFile(
                "file", "polyglot.pdf", "application/pdf", JPEG);
        assertThatThrownBy(() -> DocumentUploadValidator.validatePdfOrImage(asPdf, "devis"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // Contenu JPEG annoncé image/png : Tika détecte image/jpeg → mismatch déclaré.
        FileMagicBytesValidator.validateImageMagicBytes(JPEG);
        assertThatThrownBy(() -> DeepFileContentValidator.validateImage(JPEG, "image/png"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static byte[] minimalPdf(boolean withJavaScript) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            if (withJavaScript) {
                document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('x');"));
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
