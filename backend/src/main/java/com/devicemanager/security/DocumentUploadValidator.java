package com.devicemanager.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Règle métier / technique : partout où un document PDF est accepté,
 * une capture image (JPEG, PNG, WebP, GIF) l'est aussi.
 */
public final class DocumentUploadValidator {

    public enum Kind {
        PDF,
        IMAGE
    }

    private DocumentUploadValidator() {
    }

    /**
     * Valide qu'un fichier est un PDF (contenu et extension).
     *
     * @param file    fichier uploadé
     * @param labelFr libellé métier pour les messages d'erreur (ex. « règle de jeux »)
     */
    public static void validatePdf(MultipartFile file, String labelFr) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sélectionnez un PDF (" + labelFr + ")");
        }
        String contentType = normalizeContentType(file);
        String name = normalizeFilename(file);
        boolean pdf = contentType.contains("pdf") || name.endsWith(".pdf");
        if (!pdf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La " + labelFr + " doit être un fichier PDF");
        }
        try {
            byte[] bytes = file.getBytes();
            FileMagicBytesValidator.validatePdfMagicBytes(bytes);
            DeepFileContentValidator.validatePdf(bytes, contentType.isBlank() ? "application/pdf" : contentType);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier " + labelFr + " illisible");
        }
    }

    public static Kind validatePdfOrImage(MultipartFile file, String labelFr) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sélectionnez un PDF ou une image (" + labelFr + ")");
        }
        String contentType = normalizeContentType(file);
        String name = normalizeFilename(file);
        boolean pdf = contentType.contains("pdf") || name.endsWith(".pdf");
        boolean image = contentType.startsWith("image/")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp")
                || name.endsWith(".gif");
        if (!pdf && !image) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le " + labelFr + " doit être un PDF ou une image (JPEG, PNG, WebP…)");
        }
        try {
            byte[] bytes = file.getBytes();
            if (pdf) {
                FileMagicBytesValidator.validatePdfMagicBytes(bytes);
                DeepFileContentValidator.validatePdf(bytes, contentType.isBlank() ? "application/pdf" : contentType);
                return Kind.PDF;
            }
            FileMagicBytesValidator.validateImageMagicBytes(bytes);
            String declared = contentType.startsWith("image/") ? contentType : null;
            DeepFileContentValidator.validateImage(bytes, declared);
            return Kind.IMAGE;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier " + labelFr + " illisible");
        }
    }

    public static boolean looksLikePdf(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String contentType = normalizeContentType(file);
        String name = normalizeFilename(file);
        return contentType.contains("pdf") || name.endsWith(".pdf");
    }

    private static String normalizeContentType(MultipartFile file) {
        String raw = file.getContentType();
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFilename(MultipartFile file) {
        String raw = file.getOriginalFilename();
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT);
    }
}
