package com.devicemanager.service;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.dto.VisiteQuadriRequest;
import com.devicemanager.dto.VisiteQuadriResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.VisiteQuadritrimestrelle;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.VisiteQuadriRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Visites quadritrimestrielles : chaque SFM doit couvrir chaque marque
 * de ses compétences tous les 4 mois.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VisiteQuadriService {

    public static final int PERIOD_MONTHS = 4;
    public static final int WARN_DAYS = 7;
    public static final String LEVEL_OK = "OK";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_OVERDUE = "OVERDUE";

    private final VisiteQuadriRepository visiteQuadriRepository;
    private final SfmRepository sfmRepository;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public List<VisiteQuadriObligationResponse> status() {
        return buildObligations().stream()
                .sorted(Comparator
                        .comparing(VisiteQuadriObligationResponse::getDaysRemaining,
                                Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(VisiteQuadriObligationResponse::getSfmNom,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(VisiteQuadriObligationResponse::getMarqueLabel,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public long warningCount() {
        return buildObligations().stream()
                .filter(o -> LEVEL_WARN.equals(o.getLevel()) || LEVEL_OVERDUE.equals(o.getLevel()))
                .count();
    }

    /**
     * Résumé court pour le contexte assistant IA.
     */
    @Transactional(readOnly = true)
    public String statusSummaryForAi() {
        List<VisiteQuadriObligationResponse> all = buildObligations();
        long warn = all.stream().filter(o -> LEVEL_WARN.equals(o.getLevel())).count();
        long overdue = all.stream().filter(o -> LEVEL_OVERDUE.equals(o.getLevel())).count();
        if (warn == 0 && overdue == 0) {
            return "Visites quadritrimestrielles SFM×marque : à jour (" + all.size() + " obligation(s)).";
        }
        return "Visites quadritrimestrielles SFM×marque : "
                + overdue + " en retard, "
                + warn + " échéance(s) ≤ " + WARN_DAYS + " j, "
                + "sur " + all.size() + " obligation(s).";
    }

    @Transactional(readOnly = true)
    public List<VisiteQuadriResponse> history(Long sfmId, Long marqueId) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return visiteQuadriRepository.findHistory(atelierId, sfmId, marqueId).stream()
                .map(this::toResponse)
                .toList();
    }

    public VisiteQuadriResponse create(VisiteQuadriRequest request, String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        Long atelierId = atelier.getId();
        Sfm sfm = sfmRepository.findByIdWithMarques(request.getSfmId(), atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SFM introuvable"));
        MarqueMas marque = sfm.getMarques().stream()
                .filter(m -> m.getId().equals(request.getMarqueId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cette marque n'est pas une compétence du SFM"));

        VisiteQuadritrimestrelle saved = visiteQuadriRepository.save(VisiteQuadritrimestrelle.builder()
                .atelier(atelier)
                .sfm(sfm)
                .marque(marque)
                .dateVisite(request.getDateVisite())
                .notes(trimToNull(request.getNotes()))
                .createdBy(username != null ? username.trim() : null)
                .build());
        log.info("Création en base — Visite quadri id={} sfm={} marque={} date={} atelier={}",
                saved.getId(), sfm.getNom(), marque.getLabel(), saved.getDateVisite(), atelierId);
        return toResponse(saved);
    }

    private List<VisiteQuadriObligationResponse> buildObligations() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        LocalDate today = LocalDate.now();
        List<Sfm> sfms = sfmRepository.findAllWithMarques(atelierId);
        List<VisiteQuadriObligationResponse> result = new ArrayList<>();
        for (Sfm sfm : sfms) {
            if (sfm.getMarques() == null || sfm.getMarques().isEmpty()) {
                continue;
            }
            for (MarqueMas marque : sfm.getMarques()) {
                Optional<LocalDate> last = visiteQuadriRepository.findLastVisitDate(
                        atelierId, sfm.getId(), marque.getId());
                result.add(toObligation(sfm, marque, last.orElse(null), today));
            }
        }
        return result;
    }

    /**
     * Calcule échéance et niveau d'alerte pour un couple SFM × marque.
     * Visible pour les tests unitaires.
     */
    static VisiteQuadriObligationResponse toObligation(
            Sfm sfm,
            MarqueMas marque,
            LocalDate lastVisit,
            LocalDate today) {
        LocalDate dueDate;
        if (lastVisit == null) {
            dueDate = today;
        } else {
            dueDate = lastVisit.plusMonths(PERIOD_MONTHS);
        }
        long daysRemaining = ChronoUnit.DAYS.between(today, dueDate);
        String level;
        if (daysRemaining < 0 || lastVisit == null) {
            level = LEVEL_OVERDUE;
            if (lastVisit == null) {
                daysRemaining = 0;
            }
        } else if (daysRemaining <= WARN_DAYS) {
            level = LEVEL_WARN;
        } else {
            level = LEVEL_OK;
        }
        return VisiteQuadriObligationResponse.builder()
                .sfmId(sfm.getId())
                .sfmNom(sfm.getNom())
                .marqueId(marque.getId())
                .marqueLabel(marque.getLabel())
                .lastVisitDate(lastVisit)
                .dueDate(dueDate)
                .daysRemaining(daysRemaining)
                .level(level)
                .build();
    }

    private VisiteQuadriResponse toResponse(VisiteQuadritrimestrelle entity) {
        return VisiteQuadriResponse.builder()
                .id(entity.getId())
                .sfmId(entity.getSfm() != null ? entity.getSfm().getId() : null)
                .sfmNom(entity.getSfm() != null ? entity.getSfm().getNom() : null)
                .marqueId(entity.getMarque() != null ? entity.getMarque().getId() : null)
                .marqueLabel(entity.getMarque() != null ? entity.getMarque().getLabel() : null)
                .dateVisite(entity.getDateVisite())
                .notes(entity.getNotes())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
