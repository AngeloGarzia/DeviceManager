package com.devicemanager.service;

import com.devicemanager.dto.InterventionTechniqueRequest;
import com.devicemanager.dto.InterventionTechniqueResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.FitLigne;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionTechnique;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.User;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Interventions techniques libres sur MAS (table {@code interventions}).
 * Une visite multi-MAS crée une ligne par machine.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InterventionTechniqueService {

    private final InterventionTechniqueRepository interventionTechniqueRepository;
    private final MasRepository masRepository;
    private final UserRepository userRepository;
    private final CommandeRepository commandeRepository;
    private final InterventionRepository interventionRepository;
    private final AtelierService atelierService;
    private final FitService fitService;

    /**
     * Crée une intervention technique par MAS sélectionnée (même visite_groupe_id).
     */
    public List<InterventionTechniqueResponse> create(InterventionTechniqueRequest request, String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Atelier atelier = atelierService.requireCurrentAtelier();
        Long atelierId = atelier.getId();

        Set<Long> masIds = new LinkedHashSet<>();
        for (Long id : request.getMasIds()) {
            if (id != null) {
                masIds.add(id);
            }
        }
        if (masIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez au moins une MAS");
        }

        boolean associerFit = Boolean.TRUE.equals(request.getAssocierFit());
        if (associerFit) {
            FitService.validateSignatures(request.getSignatureAdmin(), request.getSignatureTechnicien());
        }

        Commande commande = null;
        if (request.getCommandeId() != null) {
            commande = commandeRepository.findByIdWithRelations(request.getCommandeId(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Commande introuvable dans cet atelier."));
            if (!commandeLinkedToAnyMas(commande, masIds)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La commande sélectionnée n'est pas rattachée aux MAS choisies.");
            }
        }

        Intervention bon = null;
        if (request.getBonInterventionId() != null) {
            bon = interventionRepository.findByIdWithRelations(request.getBonInterventionId(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Bon d'intervention introuvable dans cet atelier."));
            if (!bonLinkedToAnyMas(bon, masIds, atelierId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le bon d'intervention sélectionné n'est pas rattaché aux MAS choisies.");
            }
        }

        String visiteGroupeId = UUID.randomUUID().toString();
        String techNom = displayName(actor);
        List<InterventionTechnique> created = new ArrayList<>();

        for (Long masId : masIds) {
            Mas mas = masRepository.findByIdAndAtelierId(masId, atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "MAS introuvable dans cet atelier (id=" + masId + ")."));

            InterventionTechnique entity = InterventionTechnique.builder()
                    .visiteGroupeId(visiteGroupeId)
                    .atelier(atelier)
                    .mas(mas)
                    .dateIntervention(request.getDateIntervention())
                    .technicien(actor)
                    .technicienNom(techNom)
                    .emplacement(trimToNull(request.getEmplacement()))
                    .motif(request.getMotif().trim())
                    .diagnostic(trimToNull(request.getDiagnostic()))
                    .travaux(request.getTravaux().trim())
                    .observations(trimToNull(request.getObservations()))
                    .commande(commande)
                    .bonIntervention(bon)
                    .build();

            if (associerFit) {
                String motifFit = buildFitMotif(request, mas);
                FitLigne ligne = fitService.appendFromTechnicalVisit(
                        atelier,
                        mas,
                        request.getDateIntervention() != null
                                ? request.getDateIntervention().toLocalDate()
                                : LocalDate.now(),
                        motifFit,
                        request.getSignatureAdmin(),
                        request.getSignatureTechnicien(),
                        request.getSignataireAdminNom(),
                        request.getSignataireTechnicienNom() != null
                                ? request.getSignataireTechnicienNom()
                                : techNom,
                        bon);
                entity.setFit(ligne.getFit());
                entity.setFitLigne(ligne);
            }

            created.add(interventionTechniqueRepository.save(entity));
        }

        log.info("Interventions techniques créées — visite={} count={} par={} atelier={}",
                visiteGroupeId, created.size(), username, atelierId);
        return created.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InterventionTechniqueResponse> findAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return interventionTechniqueRepository.findAllByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterventionTechniqueResponse> findByMasId(Long masId) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        masRepository.findByIdAndAtelierId(masId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MAS introuvable"));
        return interventionTechniqueRepository.findByAtelierIdAndMasId(atelierId, masId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterventionTechniqueResponse findById(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        InterventionTechnique entity = interventionTechniqueRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Intervention technique introuvable"));
        return toResponse(entity);
    }

    private boolean bonLinkedToAnyMas(Intervention bon, Set<Long> masIds, Long atelierId) {
        if (bon.getMas() != null && masIds.contains(bon.getMas().getId())) {
            return true;
        }
        String machineMas = bon.getMachineMas();
        if (machineMas == null || machineMas.isBlank()) {
            return false;
        }
        String lower = machineMas.toLowerCase(Locale.ROOT).trim();
        for (Long masId : masIds) {
            Mas mas = masRepository.findByIdAndAtelierId(masId, atelierId).orElse(null);
            if (mas == null || mas.getNumero() == null) {
                continue;
            }
            String numero = mas.getNumero().toLowerCase(Locale.ROOT).trim();
            if (lower.equals(numero)
                    || lower.startsWith(numero + " — ")
                    || lower.startsWith(numero + " - ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean commandeLinkedToAnyMas(Commande commande, Set<Long> masIds) {
        if (commande.getLignes() == null) {
            return false;
        }
        for (var ligne : commande.getLignes()) {
            if (ligne.getDevice() != null
                    && ligne.getDevice().getMas() != null
                    && masIds.contains(ligne.getDevice().getMas().getId())) {
                return true;
            }
        }
        return false;
    }

    private static String buildFitMotif(InterventionTechniqueRequest request, Mas mas) {
        StringBuilder sb = new StringBuilder();
        sb.append("Intervention technique MAS ").append(mas.getNumero());
        if (request.getMotif() != null && !request.getMotif().isBlank()) {
            sb.append("\nMotif : ").append(request.getMotif().trim());
        }
        if (request.getTravaux() != null && !request.getTravaux().isBlank()) {
            sb.append("\nTravaux : ").append(request.getTravaux().trim());
        }
        String result = sb.toString();
        return result.length() > 2000 ? result.substring(0, 2000) : result;
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

    private InterventionTechniqueResponse toResponse(InterventionTechnique entity) {
        Mas mas = entity.getMas();
        if (mas != null) {
            Hibernate.initialize(mas);
            if (mas.getMarque() != null) {
                Hibernate.initialize(mas.getMarque());
            }
        }
        Intervention bon = entity.getBonIntervention();
        return InterventionTechniqueResponse.builder()
                .id(entity.getId())
                .visiteGroupeId(entity.getVisiteGroupeId())
                .dateIntervention(entity.getDateIntervention())
                .technicienNom(entity.getTechnicienNom())
                .emplacement(entity.getEmplacement())
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .masMarque(mas != null && mas.getMarque() != null ? mas.getMarque().getLabel() : null)
                .motif(entity.getMotif())
                .diagnostic(entity.getDiagnostic())
                .travaux(entity.getTravaux())
                .observations(entity.getObservations())
                .fitId(entity.getFit() != null ? entity.getFit().getId() : null)
                .fitLigneId(entity.getFitLigne() != null ? entity.getFitLigne().getId() : null)
                .commandeId(entity.getCommande() != null ? entity.getCommande().getId() : null)
                .bonInterventionId(bon != null ? bon.getId() : null)
                .bonInterventionNumero(bon != null ? bon.getNumero() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
