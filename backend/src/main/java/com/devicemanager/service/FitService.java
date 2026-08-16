package com.devicemanager.service;

import com.devicemanager.dto.FitFromMasRequest;
import com.devicemanager.dto.FitLigneRequest;
import com.devicemanager.dto.FitLigneResponse;
import com.devicemanager.dto.FitResponse;
import com.devicemanager.dto.FitSignataireDto;
import com.devicemanager.dto.FitSignatairesResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Deno;
import com.devicemanager.entity.Fit;
import com.devicemanager.entity.FitLigne;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.User;
import com.devicemanager.repository.DenoRepository;
import com.devicemanager.repository.FitLigneRepository;
import com.devicemanager.repository.FitRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Fiches d'inventaire / intervention technique (FIT) liées aux MAS.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FitService {

    private static final int MIN_SIGNATURE_LENGTH = 80;

    private final FitRepository fitRepository;
    private final FitLigneRepository fitLigneRepository;
    private final MasRepository masRepository;
    private final DenoRepository denoRepository;
    private final UserRepository userRepository;
    private final InterventionRepository interventionRepository;
    private final AtelierService atelierService;

    /**
     * Admins et techniciens du groupe de l'utilisateur connecté (combos de signature).
     */
    @Transactional(readOnly = true)
    public FitSignatairesResponse listSignataires(String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        if (actor.getGroupe() == null) {
            return FitSignatairesResponse.builder().build();
        }
        List<User> users = userRepository.findAllByGroupeId(actor.getGroupe().getId());
        List<FitSignataireDto> admins = users.stream()
                .filter(u -> Roles.ADMIN.equals(u.getRole()))
                .map(this::toSignataireDto)
                .sorted(Comparator.comparing(FitSignataireDto::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<FitSignataireDto> techniciens = users.stream()
                .filter(u -> Roles.TECHNICIEN.equals(u.getRole()) || "TECH".equals(u.getRole()))
                .map(this::toSignataireDto)
                .sorted(Comparator.comparing(FitSignataireDto::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return FitSignatairesResponse.builder()
                .admins(admins)
                .techniciens(techniciens)
                .build();
    }

    private FitSignataireDto toSignataireDto(User user) {
        String full = ((user.getPrenom() == null ? "" : user.getPrenom().trim()) + " "
                + (user.getNom() == null ? "" : user.getNom().trim())).trim();
        String display = full.isBlank() ? user.getUsername() : full;
        return FitSignataireDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .displayName(display)
                .role(user.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public List<FitResponse> findAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return fitRepository.findAllByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FitResponse findById(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Fit fit = fitRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "FIT introuvable"));
        return toResponse(fit);
    }

    @Transactional(readOnly = true)
    public Optional<FitResponse> findOptionalByMasId(Long masId) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        Mas mas = requireMas(masId, atelier.getId());
        return fitRepository.findByAtelierIdAndMasIdWithLignes(atelier.getId(), mas.getId())
                .or(() -> fitRepository.findByAtelierIdAndNumeroMachineCasinoIgnoreCase(
                        atelier.getId(), mas.getNumero()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FitResponse findByMasId(Long masId) {
        return findOptionalByMasId(masId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune FIT pour cette MAS"));
    }

    /**
     * Crée la FIT de la MAS si elle n'existe pas encore (héritage des infos machine).
     */
    public FitResponse ensureForMas(FitFromMasRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        Mas mas = requireMas(request.getMasId(), atelier.getId());
        Fit fit = ensureFitEntity(atelier, mas);
        return toResponse(fitRepository.findByIdAndAtelierId(fit.getId(), atelier.getId()).orElse(fit));
    }

    /**
     * Ajoute une ligne d'intervention technique signée sur une FIT existante.
     */
    public FitResponse addLigne(Long fitId, FitLigneRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        Fit fit = fitRepository.findByIdAndAtelierId(fitId, atelier.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "FIT introuvable"));
        validateSignatures(request.getSignatureAdmin(), request.getSignatureTechnicien());

        FitLigne ligne = buildLigneBase(request);
        inheritFromMasIfPresent(ligne, fit.getMas());
        applyOptionalOverrides(ligne, request);
        if (request.getInterventionId() != null) {
            Intervention linked = interventionRepository
                    .findByIdWithRelations(request.getInterventionId(), atelier.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Bon d'intervention introuvable dans cet atelier."));
            ligne.setIntervention(linked);
        }
        fit.addLigne(ligne);
        Fit saved = fitRepository.saveAndFlush(fit);
        log.info("FIT ligne ajoutée — fitId={} motif par atelier={}", saved.getId(), atelier.getId());
        return toResponse(fitRepository.findByIdAndAtelierId(saved.getId(), atelier.getId()).orElse(saved));
    }

    /**
     * Lorsqu'un bon d'intervention est volontairement associé à la FIT de sa MAS :
     * crée/assure la FIT puis ajoute une ligne signée liée au bon.
     */
    public void appendFromIntervention(
            Intervention intervention,
            String signatureAdmin,
            String signatureTechnicien,
            String signataireAdminNom,
            String signataireTechnicienNom) {
        Mas mas = intervention.getMas();
        if (mas == null) {
            return;
        }
        validateSignatures(signatureAdmin, signatureTechnicien);

        Atelier atelier = intervention.getAtelier();
        Fit fit = ensureFitEntity(atelier, mas);

        FitLigne ligne = FitLigne.builder()
                .dateOperation(intervention.getDateIntervention() != null
                        ? intervention.getDateIntervention().toLocalDate()
                        : LocalDate.now())
                .numeroEmplacement(trimToNull(intervention.getEmplacement()))
                .motifNatureOperations(buildMotifFromIntervention(intervention))
                .signatureAdmin(signatureAdmin.trim())
                .signatureTechnicien(signatureTechnicien.trim())
                .signataireAdminNom(trimToNull(signataireAdminNom))
                .signataireTechnicienNom(trimToNull(signataireTechnicienNom))
                .intervention(intervention)
                .build();
        inheritFromMasIfPresent(ligne, mas);
        if (ligne.getNumeroEmplacement() == null) {
            ligne.setNumeroEmplacement(trimToNull(mas.getNumeroSocle()));
        }
        persistLigne(fit, ligne);
        log.info("FIT mise à jour depuis intervention id={} fitId={} mas={}",
                intervention.getId(), fit.getId(), mas.getNumero());
    }

    /**
     * Crée une ligne FIT signée pour une visite technique libre (sans bon pièces obligatoire).
     *
     * @return la ligne créée (avec FIT rattachée)
     */
    public FitLigne appendFromTechnicalVisit(
            Atelier atelier,
            Mas mas,
            LocalDate dateOperation,
            String motifNatureOperations,
            String signatureAdmin,
            String signatureTechnicien,
            String signataireAdminNom,
            String signataireTechnicienNom,
            Intervention bonIntervention) {
        validateSignatures(signatureAdmin, signatureTechnicien);
        Fit fit = ensureFitEntity(atelier, mas);
        FitLigne ligne = FitLigne.builder()
                .dateOperation(dateOperation != null ? dateOperation : LocalDate.now())
                .motifNatureOperations(motifNatureOperations == null || motifNatureOperations.isBlank()
                        ? "Intervention technique MAS"
                        : motifNatureOperations.trim())
                .signatureAdmin(signatureAdmin.trim())
                .signatureTechnicien(signatureTechnicien.trim())
                .signataireAdminNom(trimToNull(signataireAdminNom))
                .signataireTechnicienNom(trimToNull(signataireTechnicienNom))
                .intervention(bonIntervention)
                .build();
        inheritFromMasIfPresent(ligne, mas);
        FitLigne saved = persistLigne(fit, ligne);
        log.info("FIT ligne depuis visite technique — fitId={} mas={}", fit.getId(), mas.getNumero());
        return saved;
    }

    /**
     * Persiste la ligne côté owning ({@code fit_ligne.fit_id}) pour qu'elle soit managée
     * avant tout autre agrégat (ex. intervention technique) qui la référence.
     * Évite TransientObjectException Hibernate 6 au commit.
     */
    private FitLigne persistLigne(Fit fit, FitLigne ligne) {
        ligne.setFit(fit);
        FitLigne saved = fitLigneRepository.saveAndFlush(ligne);
        if (fit.getLignes() != null
                && fit.getLignes().stream().noneMatch(l ->
                l == saved || (saved.getId() != null && saved.getId().equals(l.getId())))) {
            fit.getLignes().add(saved);
        }
        return saved;
    }

    private Fit ensureFitEntity(Atelier atelier, Mas mas) {
        return fitRepository.findByAtelierIdAndMasId(atelier.getId(), mas.getId())
                .or(() -> fitRepository.findByAtelierIdAndNumeroMachineCasinoIgnoreCase(
                        atelier.getId(), mas.getNumero()))
                .map(existing -> {
                    if (existing.getMas() == null) {
                        existing.setMas(mas);
                        return fitRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Fit created = Fit.builder()
                            .atelier(atelier)
                            .mas(mas)
                            .casinoNom(atelier.getCasino() != null ? atelier.getCasino().getNom() : null)
                            .numeroMachineCasino(mas.getNumero())
                            .dateMiseEnService(mas.getDateMiseEnService())
                            .marque(mas.getMarque() != null ? mas.getMarque().getLabel() : null)
                            .typeMachine(mas.getTypeMachine())
                            .numeroSerieMachine(mas.getNumeroSerie())
                            .dateCessation(mas.getDateCessation())
                            .destinationMachineUsagee(mas.getDestinationMachineUsagee())
                            .lignes(new ArrayList<>())
                            .build();
                    Fit saved = fitRepository.saveAndFlush(created);
                    log.info("FIT créée — id={} mas={} atelier={}",
                            saved.getId(), mas.getNumero(), atelier.getId());
                    return saved;
                });
    }

    private FitLigne buildLigneBase(FitLigneRequest request) {
        return FitLigne.builder()
                .dateOperation(request.getDateOperation())
                .numeroSocle(trimToNull(request.getNumeroSocle()))
                .numeroEmplacement(trimToNull(request.getNumeroEmplacement()))
                .numeroSerieLecteur(trimToNull(request.getNumeroSerieLecteur()))
                .tauxRedistribution(request.getTauxRedistribution())
                .valeurUnitaireMises(request.getValeurUnitaireMises())
                .motifNatureOperations(request.getMotifNatureOperations().trim())
                .signatureAdmin(request.getSignatureAdmin().trim())
                .signatureTechnicien(request.getSignatureTechnicien().trim())
                .signataireAdminNom(trimToNull(request.getSignataireAdminNom()))
                .signataireTechnicienNom(trimToNull(request.getSignataireTechnicienNom()))
                .build();
    }

    private void applyOptionalOverrides(FitLigne ligne, FitLigneRequest request) {
        if (request.getDenoId() != null) {
            Deno deno = denoRepository.findById(request.getDenoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deno introuvable"));
            ligne.setDeno(deno);
            if (ligne.getValeurUnitaireMises() == null) {
                ligne.setValeurUnitaireMises(deno.getValeur());
            }
        }
        if (request.getNumeroSocle() != null && !request.getNumeroSocle().isBlank()) {
            ligne.setNumeroSocle(request.getNumeroSocle().trim());
        }
        if (request.getTauxRedistribution() != null) {
            ligne.setTauxRedistribution(request.getTauxRedistribution());
        }
        if (request.getValeurUnitaireMises() != null) {
            ligne.setValeurUnitaireMises(request.getValeurUnitaireMises());
        }
    }

    private void inheritFromMasIfPresent(FitLigne ligne, Mas mas) {
        if (mas == null) {
            return;
        }
        if (ligne.getNumeroSocle() == null || ligne.getNumeroSocle().isBlank()) {
            ligne.setNumeroSocle(trimToNull(mas.getNumeroSocle()));
        }
        if (ligne.getTauxRedistribution() == null) {
            ligne.setTauxRedistribution(mas.getTauxRedistribution());
        }
        if (mas.getDeno() != null) {
            if (ligne.getDeno() == null) {
                ligne.setDeno(mas.getDeno());
            }
            if (ligne.getValeurUnitaireMises() == null) {
                ligne.setValeurUnitaireMises(mas.getDeno().getValeur());
            }
        }
    }

    private static String buildMotifFromIntervention(Intervention intervention) {
        StringJoiner joiner = new StringJoiner("\n");
        if (intervention.getNumero() != null) {
            joiner.add("Bon " + intervention.getNumero());
        }
        if (intervention.getMotif() != null && !intervention.getMotif().isBlank()) {
            joiner.add("Motif : " + intervention.getMotif().trim());
        }
        if (intervention.getTravaux() != null && !intervention.getTravaux().isBlank()) {
            joiner.add("Travaux : " + intervention.getTravaux().trim());
        }
        if (intervention.getLignes() != null && !intervention.getLignes().isEmpty()) {
            StringJoiner pieces = new StringJoiner(", ");
            intervention.getLignes().forEach(l -> {
                String label = l.getPieceNom() != null ? l.getPieceNom() : "pièce";
                pieces.add(label + " ×" + l.getQuantite());
            });
            joiner.add("Pièces : " + pieces);
        }
        String result = joiner.toString();
        if (result.isBlank()) {
            return "Intervention technique MAS";
        }
        return result.length() > 2000 ? result.substring(0, 2000) : result;
    }

    private Mas requireMas(Long masId, Long atelierId) {
        return masRepository.findByIdAndAtelierId(masId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "MAS introuvable dans cet atelier."));
    }

    static void validateSignatures(String signatureAdmin, String signatureTechnicien) {
        requireDrawnSignature(signatureAdmin, "admin");
        requireDrawnSignature(signatureTechnicien, "technicien");
    }

    private static void requireDrawnSignature(String value, String roleLabel) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature " + roleLabel + " est obligatoire (dessin).");
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

    private FitResponse toResponse(Fit fit) {
        Hibernate.initialize(fit.getLignes());
        if (fit.getMas() != null) {
            Hibernate.initialize(fit.getMas());
        }
        List<FitLigneResponse> lignes = fit.getLignes().stream()
                .map(this::toLigneResponse)
                .toList();
        Mas mas = fit.getMas();
        return FitResponse.builder()
                .id(fit.getId())
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .casinoNom(fit.getCasinoNom())
                .numeroMachineCasino(fit.getNumeroMachineCasino())
                .dateMiseEnService(fit.getDateMiseEnService())
                .marque(fit.getMarque())
                .typeMachine(fit.getTypeMachine())
                .numeroSerieMachine(fit.getNumeroSerieMachine())
                .numeroSerieLecteur(fit.getNumeroSerieLecteur())
                .dateCessation(fit.getDateCessation())
                .destinationMachineUsagee(fit.getDestinationMachineUsagee())
                .modeleNumero(fit.getModeleNumero())
                .referenceLegale(fit.getReferenceLegale())
                .createdAt(fit.getCreatedAt())
                .updatedAt(fit.getUpdatedAt())
                .totalLignes(lignes.size())
                .lignes(lignes)
                .build();
    }

    private FitLigneResponse toLigneResponse(FitLigne ligne) {
        Deno deno = ligne.getDeno();
        Intervention intervention = ligne.getIntervention();
        return FitLigneResponse.builder()
                .id(ligne.getId())
                .interventionId(intervention != null ? intervention.getId() : null)
                .interventionNumero(intervention != null ? intervention.getNumero() : null)
                .dateOperation(ligne.getDateOperation())
                .numeroSocle(ligne.getNumeroSocle())
                .numeroEmplacement(ligne.getNumeroEmplacement())
                .numeroSerieLecteur(ligne.getNumeroSerieLecteur())
                .tauxRedistribution(ligne.getTauxRedistribution())
                .valeurUnitaireMises(ligne.getValeurUnitaireMises())
                .denoId(deno != null ? deno.getId() : null)
                .denoLabel(deno != null ? deno.getLabel() : null)
                .motifNatureOperations(ligne.getMotifNatureOperations())
                .signatureAdmin(ligne.getSignatureAdmin())
                .signatureTechnicien(ligne.getSignatureTechnicien())
                .signataireAdminNom(ligne.getSignataireAdminNom())
                .signataireTechnicienNom(ligne.getSignataireTechnicienNom())
                .signatureDirecteur(ligne.isSignatureDirecteur())
                .createdAt(ligne.getCreatedAt())
                .build();
    }
}
