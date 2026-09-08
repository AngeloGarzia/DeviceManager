package com.devicemanager.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Contrat de stockage des fichiers uploadés (photos, PDF, documents).
 * <p>
 * Implémenté par {@link LocalStorageService} (disque + MySQL) ou
 * {@link S3StorageService} (Cloudflare R2 / S3-compatible).
 * <p>
 * En base on ne persiste que la <strong>clé objet</strong> (jamais une URL présignée).
 * Les URL d'accès pour le frontend sont produites à la lecture via
 * {@link #resolveAccessUrl(String, AccessKind)}.
 */
public interface StorageService {

    /**
     * Enregistre un fichier et retourne sa clé (et un localisateur stable, non présigné).
     */
    StoredObject store(MultipartFile file);

    /**
     * Supprime un fichier par sa clé de stockage.
     */
    void delete(String key);

    /**
     * Produit une URL utilisable par le navigateur pour lire l'objet.
     * <ul>
     *   <li>Local : {@code /uploads/{key}}</li>
     *   <li>R2/S3 : URL présignée GET temporaire</li>
     * </ul>
     *
     * @param objectKey clé stockée en base (ou localisateur legacy)
     * @param kind      durée d'expiration (média court / document plus long)
     * @return URL absolue ou chemin relatif, ou {@code null} si clé vide
     */
    String resolveAccessUrl(String objectKey, AccessKind kind);

    default String resolveAccessUrl(String objectKey) {
        return resolveAccessUrl(objectKey, AccessKind.MEDIA);
    }

    /**
     * Résout une URL d'accès à partir de la clé prioritaire, avec repli sur une URL/clé legacy.
     */
    default String resolveAccessUrl(String objectKey, String legacyUrlOrKey, AccessKind kind) {
        String key = firstNonBlank(objectKey, extractObjectKey(legacyUrlOrKey), legacyUrlOrKey);
        return resolveAccessUrl(key, kind);
    }

    /**
     * Extrait une clé objet depuis une URL S3/R2 ou un chemin {@code /uploads/…} legacy.
     */
    static String extractObjectKey(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String value = stored.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            if (value.startsWith("/uploads/")) {
                return value.substring("/uploads/".length());
            }
            return value;
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return null;
            }
            String stripped = path.startsWith("/") ? path.substring(1) : path;
            // Path-style R2 : /bucket/key… → retirer le 1er segment si présent
            int slash = stripped.indexOf('/');
            if (slash > 0 && slash < stripped.length() - 1) {
                String rest = stripped.substring(slash + 1);
                // Heuristique : clés DeviceManager commencent souvent par spare-parts/
                if (rest.startsWith("spare-parts/") || value.contains(".r2.cloudflarestorage.com")) {
                    return rest;
                }
                // Virtual-hosted : path = key entière
                if (value.contains(".amazonaws.com") || value.contains(".r2.dev")) {
                    return stripped;
                }
                return rest.isBlank() ? stripped : rest;
            }
            return stripped;
        } catch (Exception ex) {
            return value;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /** Usage d'accès : influence la durée de vie des URLs présignées. */
    enum AccessKind {
        /** Vignettes / photos UI — expiration courte. */
        MEDIA,
        /** PDF devis, documents, règles — expiration plus longue. */
        DOCUMENT
    }

    /**
     * Résultat d'un upload.
     *
     * @param key         clé interne à persister en base
     * @param url         localisateur stable (local {@code /uploads/…} ou clé S3) — <strong>jamais</strong> une URL présignée
     * @param contentType type MIME
     * @param size        taille en octets
     */
    record StoredObject(String key, String url, String contentType, long size) {
    }
}
