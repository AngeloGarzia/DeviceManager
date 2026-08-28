package com.devicemanager.service;

import com.devicemanager.dto.ArretMaintenanceAlertResponse;
import com.devicemanager.dto.ArretMaintenanceRepriseRequest;
import com.devicemanager.dto.ArretMaintenanceRequest;
import com.devicemanager.dto.ArretMaintenanceResponse;
import com.devicemanager.entity.ArretMaintenance;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.User;
import com.devicemanager.repository.ArretMaintenanceRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ArretMaintenanceService {

    private static final int MIN_SIGNATURE_LENGTH = 80;

    private final ArretMaintenanceRepository arretMaintenanceRepository;
    private final MasRepository masRepository;
    private final UserRepository userRepository;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public List<ArretMaintenanceResponse> listActive() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return arretMaintenanceRepository.findActiveByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArretMaintenanceResponse> history() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return arretMaintenanceRepository.findHistoryByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArretMaintenanceAlertResponse alertSummary() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<ArretMaintenance> active = arretMaintenanceRepository.findActiveByAtelierId(atelierId);
        List<ArretMaintenanceAlertResponse.ArretMaintenanceAlertItem> items = active.stream()
                .map(a -> ArretMaintenanceAlertResponse.ArretMaintenanceAlertItem.builder()
                        .id(a.getId())
                        .masId(a.getMas().getId())
                        .masNumero(a.getMas().getNumero())
                        .dateHeureArret(a.getDateHeureArret())
                        .build())
                .toList();
        return ArretMaintenanceAlertResponse.builder()
                .count(items.size())
                .items(items)
                .build();
    }

    public ArretMaintenanceResponse declareArret(ArretMaintenanceRequest request, String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        Mas mas = masRepository.findByIdAndAtelierId(request.getMasId(), atelier.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "MAS introuvable dans cet atelier."));
        if (arretMaintenanceRepository.existsByMasIdAndDateHeureRepriseIsNull(mas.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette MAS est déjà arrêtée pour maintenance.");
        }
        User admin = resolveUser(username);
        if (request.isRegistreTechniqueAJour()) {
            validateDrawnSignature(request.getSignatureArret(), "d'arrêt");
        }

        ArretMaintenance saved = arretMaintenanceRepository.save(ArretMaintenance.builder()
                .atelier(atelier)
                .mas(mas)
                .adminUsername(admin.getUsername())
                .adminDisplayName(displayName(admin))
                .dateHeureArret(request.getDateHeureArret() != null
                        ? request.getDateHeureArret()
                        : LocalDateTime.now())
                .motifArret(request.getMotifArret().trim())
                .registreTechniqueAJour(request.isRegistreTechniqueAJour())
                .signatureArret(trimToNull(request.getSignatureArret()))
                .signataireArretNom(trimToNull(request.getSignataireArretNom()))
                .build());
        log.info("Arrêt maintenance — mas={} id={} admin={}", mas.getNumero(), saved.getId(), username);
        return toResponse(saved);
    }

    public ArretMaintenanceResponse declareReprise(
            Long id,
            ArretMaintenanceRepriseRequest request,
            String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        ArretMaintenance arret = arretMaintenanceRepository.findByIdAndAtelierId(id, atelier.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arrêt introuvable."));
        if (!arret.isActif()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet arrêt est déjà clôturé.");
        }
        User actor = resolveUser(username);
        if (arret.isRegistreTechniqueAJour()) {
            validateDrawnSignature(request.getSignatureRedemarrage(), "de redémarrage");
        }
        arret.setDateHeureReprise(request.getDateHeureReprise() != null
                ? request.getDateHeureReprise()
                : LocalDateTime.now());
        arret.setSignatureRedemarrage(trimToNull(request.getSignatureRedemarrage()));
        arret.setSignataireRedemarrageNom(trimToNull(request.getSignataireRedemarrageNom()) != null
                ? trimToNull(request.getSignataireRedemarrageNom())
                : displayName(actor));
        ArretMaintenance saved = arretMaintenanceRepository.save(arret);
        log.info("Reprise maintenance — mas={} arretId={} user={}",
                saved.getMas().getNumero(), saved.getId(), username);
        return toResponse(saved);
    }

    private User resolveUser(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable.");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable."));
    }

    private static String displayName(User user) {
        String full = ((user.getPrenom() == null ? "" : user.getPrenom().trim()) + " "
                + (user.getNom() == null ? "" : user.getNom().trim())).trim();
        return full.isBlank() ? user.getUsername() : full;
    }

    private static void validateDrawnSignature(String value, String roleLabel) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature " + roleLabel + " est obligatoire (registre technique à jour).");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("data:image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature " + roleLabel + " doit être une image dessinée.");
        }
        if (trimmed.length() < MIN_SIGNATURE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature " + roleLabel + " est vide ou trop courte.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ArretMaintenanceResponse toResponse(ArretMaintenance arret) {
        Mas mas = arret.getMas();
        return ArretMaintenanceResponse.builder()
                .id(arret.getId())
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .masMarque(mas != null && mas.getMarque() != null ? mas.getMarque().getLabel() : null)
                .adminUsername(arret.getAdminUsername())
                .adminDisplayName(arret.getAdminDisplayName())
                .dateHeureArret(arret.getDateHeureArret())
                .dateHeureReprise(arret.getDateHeureReprise())
                .motifArret(arret.getMotifArret())
                .registreTechniqueAJour(arret.isRegistreTechniqueAJour())
                .actif(arret.isActif())
                .createdAt(arret.getCreatedAt())
                .build();
    }
}
