package com.devicemanager.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Contrat de stockage des fichiers uploadés (photos de pièces détachées).
 * <p>
 * Implémenté par {@link LocalStorageService} (disque + MySQL) ou
 * {@link S3StorageService} selon la configuration DeviceManager.
 */
public interface StorageService {

    /**
     * Enregistre un fichier et retourne sa clé et son URL publique.
     *
     * @param file fichier multipart à persister
     * @return objet stocké (clé, URL, type MIME, taille)
     */
    StoredObject store(MultipartFile file);

    /**
     * Supprime un fichier par sa clé de stockage.
     *
     * @param key identifiant du fichier (sans chemin)
     */
    void delete(String key);

    /**
     * Résultat d'un upload : métadonnées et localisation du fichier.
     *
     * @param key clé interne de stockage
     * @param url URL ou chemin HTTP d'accès
     * @param contentType type MIME
     * @param size taille en octets
     */
    record StoredObject(String key, String url, String contentType, long size) {
    }
}
