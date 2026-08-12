package com.devicemanager.service;

import com.devicemanager.dto.TimelineEventResponse;
import com.devicemanager.dto.TimelineLineDto;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.StockMouvementRepository;
import com.devicemanager.security.StockMouvementSources;
import com.devicemanager.security.TimelineEventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Agrège demandes, validations, réceptions, interventions et ajustements manuels de stock.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimelineService {

    private final CommandeRepository commandeRepository;
    private final InterventionRepository interventionRepository;
    private final StockMouvementRepository stockMouvementRepository;
    private final AtelierService atelierService;

    /**
     * Timeline de l'atelier courant, triée du plus récent au plus ancien.
     *
     * @param from  borne basse inclusive (optionnelle)
     * @param to    borne haute inclusive (optionnelle)
     * @param types filtres de types (optionnel ; vide = tous)
     */
    public List<TimelineEventResponse> findEvents(LocalDateTime from, LocalDateTime to, List<String> types) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Set<String> typeFilter = normalizeTypes(types);

        List<TimelineEventResponse> events = new ArrayList<>();
        appendOrderEvents(events, atelierId, from, to, typeFilter);
        appendInterventionEvents(events, atelierId, from, to, typeFilter);
        appendManualStockEvents(events, atelierId, from, to, typeFilter);

        events.sort(Comparator
                .comparing(TimelineEventResponse::getAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TimelineEventResponse::getRefId, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
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
                events.add(TimelineEventResponse.builder()
                        .type(TimelineEventTypes.ORDER_REQUEST)
                        .at(commande.getDateDemande())
                        .title("Demande de commande #" + commande.getId())
                        .subtitle(lignes.size() + " pièce(s) · Qté " + totalQty)
                        .acteur(commande.getTechnicienNom())
                        .refType("ORDER")
                        .refId(commande.getId())
                        .lignes(lignes)
                        .build());
            }

            if (include(typeFilter, TimelineEventTypes.ORDER_VALIDATED)
                    && commande.getDateValidation() != null
                    && inRange(commande.getDateValidation(), from, to)) {
                events.add(TimelineEventResponse.builder()
                        .type(TimelineEventTypes.ORDER_VALIDATED)
                        .at(commande.getDateValidation())
                        .title("Validation commande #" + commande.getId())
                        .subtitle(lignes.size() + " pièce(s) · Qté " + totalQty)
                        .acteur(null)
                        .refType("ORDER")
                        .refId(commande.getId())
                        .lignes(lignes)
                        .build());
            }

            if (include(typeFilter, TimelineEventTypes.ORDER_RECEIVED)
                    && commande.getDateReception() != null
                    && inRange(commande.getDateReception(), from, to)) {
                events.add(TimelineEventResponse.builder()
                        .type(TimelineEventTypes.ORDER_RECEIVED)
                        .at(commande.getDateReception())
                        .title("Réception commande #" + commande.getId())
                        .subtitle("Stock +" + totalQty)
                        .acteur(null)
                        .refType("ORDER")
                        .refId(commande.getId())
                        .deltaStock(totalQty)
                        .lignes(lignes)
                        .build());
            }
        }
    }

    private void appendInterventionEvents(
            List<TimelineEventResponse> events,
            Long atelierId,
            LocalDateTime from,
            LocalDateTime to,
            Set<String> typeFilter) {
        if (!include(typeFilter, TimelineEventTypes.INTERVENTION)) {
            return;
        }
        for (Intervention intervention : interventionRepository.findAllWithRelationsByAtelierId(atelierId)) {
            if (!inRange(intervention.getDateIntervention(), from, to)) {
                continue;
            }
            List<TimelineLineDto> lignes = intervention.getLignes() == null
                    ? List.of()
                    : intervention.getLignes().stream().map(this::toInterventionLine).toList();
            int totalDelta = lignes.stream()
                    .mapToInt(l -> l.getDelta() == null ? 0 : l.getDelta())
                    .sum();
            events.add(TimelineEventResponse.builder()
                    .type(TimelineEventTypes.INTERVENTION)
                    .at(intervention.getDateIntervention())
                    .title("Intervention " + intervention.getNumero())
                    .subtitle(intervention.getMotif())
                    .acteur(intervention.getTechnicienNom())
                    .refType("INTERVENTION")
                    .refId(intervention.getId())
                    .deltaStock(totalDelta)
                    .lignes(lignes)
                    .build());
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
            events.add(TimelineEventResponse.builder()
                    .type(TimelineEventTypes.STOCK_ADJUSTMENT)
                    .at(m.getCreatedAt())
                    .title("Ajustement stock — " + m.getPieceNom())
                    .subtitle("Stock " + m.getStockAvant() + " → " + m.getStockApres()
                            + " (" + sign + m.getDelta() + ")")
                    .acteur(m.getActeurNom())
                    .refType("DEVICE")
                    .refId(m.getDevice() != null ? m.getDevice().getId() : m.getSourceId())
                    .deltaStock(m.getDelta())
                    .lignes(List.of(line))
                    .build());
        }
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
