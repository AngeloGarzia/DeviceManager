package com.devicemanager.exception;

/**
 * Levée lorsque le stockage objet (Cloudflare R2 / S3) est temporairement indisponible
 * ou mal configuré (auth, réseau, 5xx). À distinguer de « fichier absent »
 * qui reste un cas fonctionnel (404 métier).
 * <p>
 * Traduite en HTTP 503 par {@link GlobalExceptionHandler}.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
