package com.devicemanager.service;

import com.devicemanager.dto.TimelineEventResponse;
import com.devicemanager.dto.TimelineLineDto;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Fit;
import com.devicemanager.entity.FitLigne;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.InterventionTechnique;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.FitRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.StockMouvementRepository;
import com.devicemanager.security.StockMouvementSources;
import com.devicemanager.security.TimelineEventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agrège commandes, bons, interventions techniques, FIT et ajustements de stock.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimelineService {

    private final CommandeRepository commandeRepository;
    private final InterventionRepository interventionRepository;
    private final InterventionTechniqueRepository interventionTechniqueRepository;
    private final FitRepository fitRepository;
    private final MasRepository masRepository;
    private final StockMouvementRepository stockMouvementRepository;
    private final AtelierService atelierService;

    /**
     * Timeline de l'atelier courant, triée du plus récent au plus ancien.
     *
     * @param from  borne basse inclusive (optionnelle)
     * @param to    borne haute inclusive (optionnelle)
     * @param types filtres de types (optionnel ; vide = tous)
     * @param masId si renseigné, ne conserve que les événements liés à cette MAS
     *              (bons, interventions techniques, FIT) — exclut commandes / stock global
     */
    public List<TimelineEventResponse> findEvents(
            LocalDateTime from,
            LocalDateTime to,
            List<String> types,
            Long masId) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Set<String> typeFilter = normalizeTypes(types);
        String masNumero = null;
        if (masId != null) {
            Mas mas = masRepository.findByIdAndAtelierId(masId, atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MAS introuvable"));
            masNumero = mas.getNumero();
        }

        List<TimelineEventResponse> events = new ArrayList<>();
        if (masId == null) {
            appendOrderEvents(events, atelierId, from, to, typeFilter);
            appendManualStockEvents(events, atelierId, from, to, typeFilter);
        }
        appendBonEvents(events, atelierId, from, to, typeFilter, masId, masNumero);
        appendTechniqueEvents(events, atelierId, from, to, typeFilter, masId);
        appendFitEvents(events, atelierId, from, to, typeFilter, masId);

        events.sort(Comparator
                .comparing(TimelineEventResponse::getAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TimelineEventResponse::getRefId, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    /**
     * Identifiants des MAS de l'atelier courant qui ont déjà des données de suivi
     * (bons d'intervention, interventions techniques ou FIT).
     */
    public List<Long> findMasIdsWithSuivi() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(interventionRepository.findDistinctMasIdsByAtelierId(atelierId));
        ids.addAll(interventionTechniqueRepository.findDistinctMasIdsByAtelierId(atelierId));
        ids.addAll(fitRepository.findDistinctMasIdsByAtelierId(atelierId));

        List<String> orphanLabels = interventionRepository.findOrphanMachineMasLabelsByAtelierId(atelierId);
        if (!orphanLabels.isEmpty()) {
            List<Mas> allMas = masRepository.findAllByAtelierId(atelierId);
            for (String label : orphanLabels) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                String lower = label.toLowerCase(Locale.ROOT).trim();
                for (Mas mas : allMas) {
                    if (mas.getNumero() == null || mas.getId() == null) {
                        continue;
                    }
                    String numero = mas.getNumero().toLowerCase(Locale.ROOT).trim();
                    if (lower.equals(numero)
                            || lower.startsWith(numero + " — ")
                            || lower.startsWith(numero + " - ")) {
                        ids.add(mas.getId());
                    }
                }
            }
        }
        return ids.stream().sorted().collect(Collectors.toList());
    }

    private void appendOrderEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter) {
        for (Commande commande : commandeRepository.findAllWithRelationsOrderByDateDesc(atelierId)) {
            List<TimelineLineDto> lignes = orderLines(commande);
            int totalQty = lignes.stream().mapToInt(l -> l.getQuantite() == null ? 0 : l.getQuantite()).sum();

            if (include(typeFilter, TimelineEventTypes.ORDER_REQUEST)
                    && inRange(commande.getDateDemande(), from, to)) {
                events.add(event(
                        TimelineEventTypes.ORDER_REQUEST,
                        commande.getDateDemande(),
                        "Demande de commande #" + commande.getId(),
                        lignes.size() + " pièce(s) · Qté " + totalQty,
                        commande.getTechnicienNom(),
                        "ORDER",
                        commande.getId(),
                        null,
                        null,
                        null,
                        lignes));
            }

            if (include(typeFilter, TimelineEventTypes.ORDER_VALIDATED)
                    && commande.getDateValidation() != null
                    && inRange(commande.getDateValidation(), from, to)) {
                events.add(event(
                        TimelineEventTypes.ORDER_VALIDATED,
                        commande.getDateValidation(),
                        "Validation commande #" + commande.getId(),
                        lignes.size() + " pièce(s) · Qté " + totalQty,
                        null,
                        "ORDER",
                        commande.getId(),
                        null,
                        null,
                        null,
                        lignes));
            }

            if (include(typeFilter, TimelineEventTypes.ORDER_RECEIVED)
                    && commande.getDateReception() != null
                    && inRange(commande.getDateReception(), from, to)) {
                events.add(event(
                        TimelineEventTypes.ORDER_RECEIVED,
                        commande.getDateReception(),
                        "Réception commande #" + commande.getId(),
                        "Stock +" + totalQty,
                        null,
                        "ORDER",
                        commande.getId(),
                        null,
                        null,
                        totalQty,
                        lignes));
            }
        }
    }

    private void appendBonEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter,
            Long masId,
            String masNumero) {
        if (!include(typeFilter, TimelineEventTypes.INTERVENTION)) {
            return;
        }
        List<Intervention> list = masId != null
                ? interventionRepository.findByAtelierAndMas(atelierId, masId, masNumero)
                : interventionRepository.findAllWithRelationsByAtelierId(atelierId);
        for (Intervention intervention : list) {
            if (!inRange(intervention.getDateIntervention(), from, to)) {
                continue;
            }
            List<TimelineLineDto> lignes = intervention.getLignes() == null
                    ? List.of()
                    : intervention.getLignes().stream().map(this::toInterventionLine).toList();
            int totalDelta = lignes.stream()
                    .mapToInt(l -> l.getDelta() == null ? 0 : l.getDelta())
                    .sum();
            Mas mas = intervention.getMas();
            events.add(event(
                    TimelineEventTypes.INTERVENTION,
                    intervention.getDateIntervention(),
                    "Bon " + intervention.getNumero(),
                    intervention.getMotif(),
                    intervention.getTechnicienNom(),
                    "INTERVENTION",
                    intervention.getId(),
                    mas != null ? mas.getId() : null,
                    mas != null ? mas.getNumero() : intervention.getMachineMas(),
                    totalDelta,
                    lignes));
        }
    }

    private void appendTechniqueEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter,
            Long masId) {
        if (!include(typeFilter, TimelineEventTypes.INTERVENTION_TECHNIQUE)) {
            return;
        }
        List<InterventionTechnique> list = masId != null
                ? interventionTechniqueRepository.findByAtelierIdAndMasId(atelierId, masId)
                : interventionTechniqueRepository.findAllByAtelierId(atelierId);
        for (InterventionTechnique it : list) {
            if (!inRange(it.getDateIntervention(), from, to)) {
                continue;
            }
            Mas mas = it.getMas();
            String links = buildTechniqueLinks(it);
            events.add(event(
                    TimelineEventTypes.INTERVENTION_TECHNIQUE,
                    it.getDateIntervention(),
                    "Intervention technique — " + (mas != null ? mas.getNumero() : "?"),
                    it.getMotif() + (links.isEmpty() ? "" : " · " + links),
                    it.getTechnicienNom(),
                    "INTERVENTION_TECHNIQUE",
                    it.getId(),
                    mas != null ? mas.getId() : null,
                    mas != null ? mas.getNumero() : null,
                    null,
                    List.of()));
        }
    }

    private void appendFitEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter,
            Long masId) {
        if (!include(typeFilter, TimelineEventTypes.FIT)) {
            return;
        }
        List<Fit> fits;
        if (masId != null) {
            fits = fitRepository.findByAtelierIdAndMasIdWithLignes(atelierId, masId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            fits = fitRepository.findAllByAtelierId(atelierId);
        }
        for (Fit fit : fits) {
            Mas mas = fit.getMas();
            if (fit.getLignes() == null) {
                continue;
            }
            for (FitLigne ligne : fit.getLignes()) {
                LocalDateTime at = ligne.getDateOperation() != null
                        ? ligne.getDateOperation().atStartOfDay()
                        : ligne.getCreatedAt();
                if (!inRange(at, from, to)) {
                    continue;
                }
                String subtitle = ligne.getMotifNatureOperations();
                if (subtitle != null && subtitle.length() > 160) {
                    subtitle = subtitle.substring(0, 157) + "…";
                }
                events.add(event(
                        TimelineEventTypes.FIT,
                        at,
                        "FIT — Machine " + fit.getNumeroMachineCasino(),
                        subtitle,
                        ligne.getSignataireTechnicienNom() != null
                                ? ligne.getSignataireTechnicienNom()
                                : ligne.getSignataireAdminNom(),
                        "FIT",
                        fit.getId(),
                        mas != null ? mas.getId() : null,
                        mas != null ? mas.getNumero() : fit.getNumeroMachineCasino(),
                        null,
                        List.of()));
            }
        }
    }

    private void appendManualStockEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter) {
        if (!include(typeFilter, TimelineEventTypes.STOCK_ADJUSTMENT)) {
            return;
        }
        List<StockMouvement> mouvements = (from == null && to == null)
                ? stockMouvementRepository.findByAtelierAndSourceType(atelierId, StockMouvementSources.MANUAL)
                : stockMouvementRepository.findByAtelierAndSourceTypeBetween(
                        atelierId,
                        StockMouvementSources.MANUAL,
                        from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0),
                        to != null ? to : LocalDateTime.of(9999, 12, 31, 23, 59));
        for (StockMouvement m : mouvements) {
            TimelineLineDto line = TimelineLineDto.builder()
                    .deviceId(m.getDevice() != null ? m.getDevice().getId() : null)
                    .pieceNom(m.getPieceNom())
                    .pieceReference(m.getPieceReference())
                    .quantite(Math.abs(m.getDelta() == null ? 0 : m.getDelta()))
                    .stockAvant(m.getStockAvant())
                    .stockApres(m.getStockApres())
                    .delta(m.getDelta())
                    .build();
            String sign = (m.getDelta() != null && m.getDelta() > 0) ? "+" : "";
            events.add(event(
                    TimelineEventTypes.STOCK_ADJUSTMENT,
                    m.getCreatedAt(),
                    "Ajustement stock — " + m.getPieceNom(),
                    "Stock " + m.getStockAvant() + " → " + m.getStockApres()
                            + " (" + sign + m.getDelta() + ")",
                    m.getActeurNom(),
                    "DEVICE",
                    m.getDevice() != null ? m.getDevice().getId() : m.getSourceId(),
                    null,
                    null,
                    m.getDelta(),
                    List.of(line)));
        }
    }

    private static String buildTechniqueLinks(InterventionTechnique it) {
        List<String> parts = new ArrayList<>();
        if (it.getFit() != null) {
            parts.add("FIT");
        }
        if (it.getCommande() != null) {
            parts.add("Commande #" + it.getCommande().getId());
        }
        if (it.getBonIntervention() != null) {
            parts.add("Bon " + it.getBonIntervention().getNumero());
        }
        return String.join(" · ", parts);
    }

    private TimelineEventResponse event(
            String type,
            LocalDateTime at,
            String title,
            String subtitle,
            String acteur,
            String refType,
            Long refId,
            Long masId,
            String masNumero,
            Integer deltaStock,
            List<TimelineLineDto> lignes) {
        return TimelineEventResponse.builder()
                .type(type)
                .column(TimelineEventTypes.columnFor(type))
                .at(at)
                .title(title)
                .subtitle(subtitle)
                .acteur(acteur)
                .refType(refType)
                .refId(refId)
                .masId(masId)
                .masNumero(masNumero)
                .deltaStock(deltaStock)
                .lignes(lignes)
                .build();
    }

    private List<TimelineLineDto> orderLines(Commande commande) {
        if (commande.getLignes() == null) {
            return List.of();
        }
        return commande.getLignes().stream().map(this::toOrderLine).toList();
    }

    private TimelineLineDto toOrderLine(CommandeLigne ligne) {
        Device device = ligne.getDevice();
        return TimelineLineDto.builder()
                .deviceId(device != null ? device.getId() : null)
                .pieceNom(device != null ? device.getNom() : null)
                .pieceReference(device != null ? device.getReference() : null)
                .quantite(ligne.getQuantite())
                .build();
    }

    private TimelineLineDto toInterventionLine(InterventionLigne ligne) {
        int qty = ligne.getQuantite() == null ? 0 : ligne.getQuantite();
        return TimelineLineDto.builder()
                .deviceId(ligne.getDevice() != null ? ligne.getDevice().getId() : null)
                .pieceNom(ligne.getPieceNom())
                .pieceReference(ligne.getPieceReference())
                .quantite(qty)
                .stockAvant(ligne.getStockAvant())
                .stockApres(ligne.getStockApres())
                .delta(-qty)
                .build();
    }

    private static boolean inRange(LocalDateTime at, LocalDateTime from, LocalDateTime to) {
        if (at == null) {
            return false;
        }
        if (from != null && at.isBefore(from)) {
            return false;
        }
        return to == null || !at.isAfter(to);
    }

    private static boolean include(Set<String> filter, String type) {
        return filter.isEmpty() || filter.contains(type);
    }

    private static Set<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String raw : types) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                String t = part.trim().toUpperCase(Locale.ROOT);
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }
}
