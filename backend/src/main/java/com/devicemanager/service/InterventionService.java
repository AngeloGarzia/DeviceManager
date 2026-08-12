package com.devicemanager.service;

import com.devicemanager.dto.InterventionLineResponse;
import com.devicemanager.dto.InterventionRequest;
import com.devicemanager.dto.InterventionResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.User;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.StockMouvementSources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Création et consultation des bons d'intervention (consommation de pièces détachées).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AtelierService atelierService;
    private final StockMouvementService stockMouvementService;

    /**
     * Archive un bon d'intervention et décrémente le stock des pièces consommées.
     */
    public InterventionResponse create(InterventionRequest request, String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Atelier atelier = atelierService.requireCurrentAtelier();
        Long atelierId = atelier.getId();

        Map<Long, Integer> quantities = mergeQuantities(request.getLignes());
        if (quantities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ajoutez au moins une pièce détachée consommée");
        }

        Intervention intervention = Intervention.builder()
                .numero(nextNumero(atelierId, request.getDateIntervention()))
                .dateIntervention(request.getDateIntervention())
                .technicien(actor)
                .technicienNom(displayName(actor))
                .atelier(atelier)
                .emplacement(trimToNull(request.getEmplacement()))
                .machineMas(trimToNull(request.getMachineMas()))
                .motif(request.getMotif().trim())
                .diagnostic(trimToNull(request.getDiagnostic()))
                .travaux(request.getTravaux().trim())
                .observations(trimToNull(request.getObservations()))
                .lignes(new ArrayList<>())
                .build();

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Device device = deviceRepository.findByIdWithRelations(entry.getKey(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Pièce détachée introuvable dans cet atelier."));
            int qty = entry.getValue();
            int stockAvant = Math.max(0, device.getStock());
            if (qty > stockAvant) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuffisant pour « " + device.getNom() + " » (disponible : "
                                + stockAvant + ", demandé : " + qty + ").");
            }
            int stockApres = stockAvant - qty;
            device.setStock(stockApres);
            deviceRepository.save(device);

            InterventionLigne ligne = InterventionLigne.builder()
                    .device(device)
                    .pieceNom(device.getNom())
                    .pieceReference(device.getReference())
                    .quantite(qty)
                    .stockAvant(stockAvant)
                    .stockApres(stockApres)
                    .build();
            intervention.addLigne(ligne);
            log.info("Stock consommé — Pièce id={} -{} → stock={} (bon {})",
                    device.getId(), qty, stockApres, intervention.getNumero());
        }

        Intervention saved = interventionRepository.saveAndFlush(intervention);
        String acteurNom = displayName(actor);
        for (InterventionLigne ligne : saved.getLignes()) {
            Device device = ligne.getDevice();
            if (device == null) {
                continue;
            }
            stockMouvementService.record(
                    atelier,
                    device,
                    ligne.getStockAvant(),
                    ligne.getStockApres(),
                    StockMouvementSources.INTERVENTION,
                    saved.getId(),
                    acteurNom);
        }
        log.info("Archivage en base — Bon intervention id={} numero={} par={} pièces={} atelier={}",
                saved.getId(), saved.getNumero(), username, saved.getLignes().size(), atelierId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InterventionResponse> findAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return interventionRepository.findAllWithRelationsByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterventionResponse findById(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Intervention intervention = interventionRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bon d'intervention introuvable"));
        return toResponse(intervention);
    }

    private Map<Long, Integer> mergeQuantities(List<InterventionRequest.InterventionLineDto> lignes) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (lignes == null) {
            return quantities;
        }
        for (InterventionRequest.InterventionLineDto line : lignes) {
            if (line == null || line.getDeviceId() == null || line.getQuantite() == null) {
                continue;
            }
            int qty = Math.max(0, line.getQuantite());
            if (qty < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La quantité doit être au moins 1");
            }
            quantities.merge(line.getDeviceId(), qty, Integer::sum);
        }
        return quantities;
    }

    private String nextNumero(Long atelierId, LocalDateTime date) {
        int year = date != null ? date.getYear() : LocalDateTime.now().getYear();
        long seq = interventionRepository.countByAtelierIdAndYear(atelierId, year) + 1;
        return String.format("BI-%d-%d-%05d", atelierId, year, seq);
    }

    private static String displayName(User user) {
        String full = ((user.getPrenom() == null ? "" : user.getPrenom().trim()) + " "
                + (user.getNom() == null ? "" : user.getNom().trim())).trim();
        return full.isBlank() ? user.getUsername() : full;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private InterventionResponse toResponse(Intervention intervention) {
        Hibernate.initialize(intervention.getLignes());
        List<InterventionLineResponse> lines = intervention.getLignes().stream()
                .map(l -> {
                    Device d = l.getDevice();
                    if (d != null) {
                        Hibernate.initialize(d.getPhotos());
                    }
                    return InterventionLineResponse.builder()
                            .id(l.getId())
                            .deviceId(d != null ? d.getId() : null)
                            .pieceNom(l.getPieceNom())
                            .pieceReference(l.getPieceReference())
                            .quantite(l.getQuantite())
                            .stockAvant(l.getStockAvant())
                            .stockApres(l.getStockApres())
                            .photoUrl(d != null ? d.getPhotoUrl() : null)
                            .build();
                })
                .toList();
        int totalQty = lines.stream().mapToInt(l -> l.getQuantite() == null ? 0 : l.getQuantite()).sum();
        return InterventionResponse.builder()
                .id(intervention.getId())
                .numero(intervention.getNumero())
                .dateIntervention(intervention.getDateIntervention())
                .technicienNom(intervention.getTechnicienNom())
                .emplacement(intervention.getEmplacement())
                .machineMas(intervention.getMachineMas())
                .motif(intervention.getMotif())
                .diagnostic(intervention.getDiagnostic())
                .travaux(intervention.getTravaux())
                .observations(intervention.getObservations())
                .createdAt(intervention.getCreatedAt())
                .totalPieces(lines.size())
                .totalQuantite(totalQty)
                .lignes(lines)
                .build();
    }
}
