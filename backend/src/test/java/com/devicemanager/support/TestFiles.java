package com.devicemanager.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Binaires de test valides pour la validation upload (magic + Tika + PDFBox).
 */
public final class TestFiles {

    private TestFiles() {
    }

    /** PDF minimal sans JavaScript. */
    public static byte[] minimalPdf() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de générer un PDF de test", ex);
        }
    }
}
