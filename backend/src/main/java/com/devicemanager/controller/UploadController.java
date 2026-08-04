package com.devicemanager.controller;

import com.devicemanager.service.LocalStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur de servage des fichiers uploadés en stockage local.
 * <p>
 * Actif lorsque S3 est désactivé ; sert les photos de pièces détachées
 * depuis le disque ou la copie MySQL (Render). Les clés proviennent du
 * stockage associé à l'atelier courant lors de l'upload.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(LocalStorageService.class)
public class UploadController {

    private final LocalStorageService localStorageService;

    /**
     * Retourne le contenu binaire d'un fichier uploadé par sa clé.
     *
     * @param filename clé/nom du fichier (sans chemin)
     * @return octets de l'image avec type MIME et en-tête cache, ou {@code 404} si absent
     */
    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<byte[]> get(@PathVariable String filename) {
        return localStorageService.load(filename)
                .map(obj -> {
                    MediaType mediaType = MediaType.IMAGE_JPEG;
                    if (obj.contentType() != null && !obj.contentType().isBlank()) {
                        try {
                            mediaType = MediaType.parseMediaType(obj.contentType());
                        } catch (Exception ignored) {
                            // keep image/jpeg
                        }
                    }
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                            .contentType(mediaType)
                            .body(obj.data());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
