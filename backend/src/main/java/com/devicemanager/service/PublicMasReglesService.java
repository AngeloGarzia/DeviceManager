package com.devicemanager.service;

import com.devicemanager.dto.PublicMasReglesResponse;
import com.devicemanager.dto.PublicRegleJeuxResponse;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.RegleJeux;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.RegleJeuxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Accès public en lecture seule aux règles de jeux d'une MAS, via jeton opaque (QR).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PublicMasReglesService {

    private final MasRepository masRepository;
    private final RegleJeuxRepository regleJeuxRepository;
    private final StorageService storageService;

    public PublicMasReglesResponse findByToken(String token) {
        Mas mas = requireMasByToken(token);
        List<PublicRegleJeuxResponse> regles = mas.getReglesJeux() == null
                ? List.of()
                : mas.getReglesJeux().stream()
                        .sorted(Comparator.comparing(RegleJeux::getLabel, String.CASE_INSENSITIVE_ORDER))
                        .map(this::toPublicRegle)
                        .toList();
        return PublicMasReglesResponse.builder()
                .masNumero(mas.getNumero())
                .marqueLabel(mas.getMarque() != null ? mas.getMarque().getLabel() : null)
                .regles(regles)
                .build();
    }

    public ResponseEntity<byte[]> downloadRegleFile(String token, Long regleId) {
        Mas mas = requireMasByToken(token);
        boolean linked = mas.getReglesJeux() != null
                && mas.getReglesJeux().stream().anyMatch(r -> regleId.equals(r.getId()));
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        RegleJeux regle = regleJeuxRepository.findById(regleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        String key = firstNonBlank(regle.getFileKey(), StorageService.extractObjectKey(regle.getFileUrl()));
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        var loaded = storageService.load(key);
        if (loaded.isEmpty() && regle.getFileUrl() != null && !regle.getFileUrl().equals(key)) {
            loaded = storageService.load(regle.getFileUrl());
        }
        if (loaded.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        StorageService.StoredObjectBytes obj = loaded.get();
        MediaType mediaType = MediaType.APPLICATION_PDF;
        String declared = firstNonBlank(obj.contentType(), regle.getContentType());
        if (declared != null) {
            try {
                mediaType = MediaType.parseMediaType(declared);
            } catch (Exception ignored) {
                // keep pdf
            }
        }
        String filename = regle.getOriginalName() != null && !regle.getOriginalName().isBlank()
                ? regle.getOriginalName()
                : "regle-jeux.pdf";
        String safeName = filename.replace("\"", "");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(obj.data());
    }

    private Mas requireMasByToken(String token) {
        String normalized = token == null ? "" : token.trim();
        if (normalized.isBlank() || normalized.length() > 64) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien invalide ou expiré");
        }
        return masRepository.findByPublicAccessTokenWithRegles(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien invalide ou expiré"));
    }

    private PublicRegleJeuxResponse toPublicRegle(RegleJeux regle) {
        return PublicRegleJeuxResponse.builder()
                .id(regle.getId())
                .label(regle.getLabel())
                .description(regle.getDescription())
                .originalName(regle.getOriginalName())
                .contentType(regle.getContentType())
                .fileSize(regle.getFileSize())
                .build();
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
}
