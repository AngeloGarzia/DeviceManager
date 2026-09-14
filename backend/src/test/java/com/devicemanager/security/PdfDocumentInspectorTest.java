package com.devicemanager.security;

import com.devicemanager.support.TestFiles;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentInspectorTest {

    @Test
    void inspect_missingBytes() {
        PdfDocumentInspector.Result result = PdfDocumentInspector.inspect(null);
        assertThat(result.present()).isFalse();
        assertThat(result.valid()).isFalse();
        assertThat(result.readable()).isFalse();
    }

    @Test
    void inspect_rejectsNonPdf() {
        PdfDocumentInspector.Result result = PdfDocumentInspector.inspect("not-a-pdf".getBytes());
        assertThat(result.present()).isTrue();
        assertThat(result.valid()).isFalse();
        assertThat(result.readable()).isFalse();
    }

    @Test
    void inspect_blankPagePdf_isValidButNotReadable() {
        PdfDocumentInspector.Result result = PdfDocumentInspector.inspect(TestFiles.minimalPdf());
        assertThat(result.present()).isTrue();
        assertThat(result.valid()).isTrue();
        assertThat(result.readable()).isFalse();
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.message()).containsIgnoringCase("non extractible");
    }

    @Test
    void inspect_textPdf_isValidAndReadable() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 750);
                cs.showText("Regle de jeux Book of Ra");
                cs.endText();
            }
            document.save(out);
            pdf = out.toByteArray();
        }

        PdfDocumentInspector.Result result = PdfDocumentInspector.inspect(pdf);
        assertThat(result.present()).isTrue();
        assertThat(result.valid()).isTrue();
        assertThat(result.readable()).isTrue();
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.message()).containsIgnoringCase("lisible");
    }
}
