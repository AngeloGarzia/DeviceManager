package com.devicemanager.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validation des magic bytes des formats image acceptés (JPEG, PNG, GIF, WEBP).
 */
public final class FileMagicBytesValidator {

    private FileMagicBytesValidator() {
    }

    /**
     * Vérifie que les octets correspondent à un format image supporté.
     *
     * @param data contenu du fichier (au moins les premiers octets)
     * @throws ResponseStatusException {@code 400} si le format n'est pas reconnu
     */
    public static void validateImageMagicBytes(byte[] data) {
        if (data == null || data.length < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image illisible ou format non supporté");
        }
        if (isJpeg(data) || isPng(data) || isGif(data) || isWebp(data)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image illisible ou format non supporté");
    }

    private static boolean isJpeg(byte[] data) {
        return data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;
    }

    private static boolean isPng(byte[] data) {
        return data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
    }

    private static boolean isGif(byte[] data) {
        return data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    private static boolean isWebp(byte[] data) {
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }
}
