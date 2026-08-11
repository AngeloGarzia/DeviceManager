package com.devicemanager.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contrôle basique du contenu binaire d'une image (magic bytes).
 */
public final class FileMagicBytesValidator {

    private FileMagicBytesValidator() {
    }

    /**
     * Vérifie que les octets correspondent à un format image supporté (JPEG, PNG, GIF, WebP).
     *
     * @param data contenu du fichier
     */
    public static void validateImageMagicBytes(byte[] data) {
        if (data == null || data.length < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Photo illisible ou format non pris en charge");
        }
        if (isJpeg(data) || isPng(data) || isGif(data) || isWebp(data)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Photo illisible ou format non pris en charge");
    }

    private static boolean isJpeg(byte[] d) {
        return d[0] == (byte) 0xFF && d[1] == (byte) 0xD8 && d[2] == (byte) 0xFF;
    }

    private static boolean isPng(byte[] d) {
        return d[0] == (byte) 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47;
    }

    private static boolean isGif(byte[] d) {
        return d[0] == 'G' && d[1] == 'I' && d[2] == 'F';
    }

    private static boolean isWebp(byte[] d) {
        return d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }
}
