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
     * Valide le fichier et renvoie son type (PDF ou image).
     *
     * @param file    fichier uploadé
     * @param labelFr libellé métier pour les messages d'erreur (ex. « devis », « document »)
     * @return type détecté
     */
    public static Kind validatePdfOrImage(MultipartFile file, String labelFr) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sélectionnez un PDF ou une image (" + labelFr + ")");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
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
                return Kind.PDF;
            }
            FileMagicBytesValidator.validateImageMagicBytes(bytes);
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
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        return contentType.contains("pdf") || name.endsWith(".pdf");
    }
}
