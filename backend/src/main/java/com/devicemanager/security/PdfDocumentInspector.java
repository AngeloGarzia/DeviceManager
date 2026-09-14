package com.devicemanager.security;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Inspecte un PDF déjà stocké : structure valide (ouvrable) et texte extractible.
 * Contrairement à {@link DocumentUploadValidator}, ne lève pas d'exception —
 * renvoie un résultat pour affichage catalogue.
 */
public final class PdfDocumentInspector {

    private static final Tika TIKA = new Tika();

    private PdfDocumentInspector() {
    }

    /**
     * @param data octets du fichier (peut être null / vide)
     */
    public static Result inspect(byte[] data) {
        if (data == null || data.length == 0) {
            return Result.missing("PDF introuvable ou vide");
        }
        try {
            FileMagicBytesValidator.validatePdfMagicBytes(data);
        } catch (ResponseStatusException ex) {
            return Result.invalid(ex.getReason() != null ? ex.getReason() : "En-tête PDF invalide");
        }
        try {
            String detected = TIKA.detect(data).toLowerCase(Locale.ROOT);
            if (!"application/pdf".equals(detected)) {
                return Result.invalid("Le fichier n'est pas un PDF valide");
            }
        } catch (Exception ex) {
            return Result.invalid("Fichier illisible");
        }
        try {
            DeepFileContentValidator.validatePdf(data, "application/pdf");
        } catch (ResponseStatusException ex) {
            return Result.invalid(ex.getReason() != null ? ex.getReason() : "PDF invalide");
        }

        try (PDDocument document = Loader.loadPDF(data)) {
            int pages = document.getNumberOfPages();
            if (pages <= 0) {
                return new Result(true, false, false, 0,
                        "PDF sans page — document illisible");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            boolean readable = text != null && !text.isBlank();
            if (readable) {
                return new Result(true, true, true, pages,
                        "PDF valide et lisible (" + pages + " page" + (pages > 1 ? "s" : "") + ")");
            }
            return new Result(true, true, false, pages,
                    "PDF valide mais texte non extractible (scan image ou PDF protégé)");
        } catch (Exception ex) {
            return Result.invalid("Impossible d'ouvrir le PDF : "
                    + (ex.getMessage() != null ? ex.getMessage() : "fichier corrompu"));
        }
    }

    /**
     * @param present  fichier présent en stockage
     * @param valid    structure PDF OK (magic + Tika + PDFBox)
     * @param readable texte extractible
     * @param pageCount nombre de pages si valide, sinon 0
     * @param message  libellé FR pour l'UI
     */
    public record Result(boolean present, boolean valid, boolean readable, int pageCount, String message) {
        public static Result missing(String message) {
            return new Result(false, false, false, 0, message);
        }

        public static Result invalid(String message) {
            return new Result(true, false, false, 0, message);
        }
    }
}
