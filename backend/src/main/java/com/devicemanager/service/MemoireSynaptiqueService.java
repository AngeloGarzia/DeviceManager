package com.devicemanager.service;

import com.devicemanager.dto.MemoireSynaptiqueResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.AtelierMemoireSynaptique;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.event.AtelierSituationEvent;
import com.devicemanager.repository.ArretMaintenanceRepository;
import com.devicemanager.repository.AtelierMemoireSynaptiqueRepository;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.security.OrderStatuses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Mémoire synaptique IA : snapshot persistant de la situation d'un atelier,
 * mis à jour à chaque événement métier et injecté dans le chat assistant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoireSynaptiqueService {

    private static final int MAX_FACTS = 40;
    private static final int MAX_OVERVIEW_CHARS = 3500;
    private static final int MAX_FACT_CHARS = 280;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AtelierMemoireSynaptiqueRepository memoireRepository;
    private final AtelierService atelierService;
    private final DeviceRepository deviceRepository;
    private final MasRepository masRepository;
    private final SfmRepository sfmRepository;
    private final TodoTacheRepository todoTacheRepository;
    private final CommandeRepository commandeRepository;
    private final InterventionRepository interventionRepository;
    private final InterventionTechniqueRepository interventionTechniqueRepository;
    private final ArretMaintenanceRepository arretMaintenanceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Écoute les événements métier et met à jour la mémoire de l'atelier concerné.
     */
    @EventListener
    @Transactional
    public void onSituationEvent(AtelierSituationEvent event) {
        if (event == null || event.atelierId() == null) {
            return;
        }
        try {
            recordFact(event.atelierId(), event.type(), event.fact());
        } catch (Exception ex) {
            log.warn("Mémoire synaptique non mise à jour (atelier={}): {}",
                    event.atelierId(), ex.getMessage());
        }
    }

    /**
     * Texte à injecter dans le prompt chat (overview + faits pertinents pour la question).
     */
    @Transactional
    public String contextForAiChat(String userQuestion) {
        Atelier atelier;
        try {
            atelier = atelierService.requireCurrentAtelier();
        } catch (Exception ex) {
            return "";
        }
        AtelierMemoireSynaptique row = ensureAndRefresh(atelier);
        String overview = row.getOverview() == null ? "" : row.getOverview().trim();
        List<String> facts = readFacts(row.getFactsJson());
        List<String> relevant = filterRelevantFacts(facts, userQuestion);
        StringBuilder sb = new StringBuilder();
        if (!overview.isBlank()) {
            sb.append(overview);
        }
        if (!relevant.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\nFaits récents pertinents :\n");
            } else {
                sb.append("Faits récents :\n");
            }
            for (String f : relevant) {
                sb.append("- ").append(f).append('\n');
            }
        }
        String out = sb.toString().trim();
        return out.length() > MAX_OVERVIEW_CHARS + 1500
                ? out.substring(0, MAX_OVERVIEW_CHARS + 1500)
                : out;
    }

    @Transactional(readOnly = true)
    public MemoireSynaptiqueResponse currentForApi() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        AtelierMemoireSynaptique row = memoireRepository.findByAtelierId(atelier.getId())
                .orElse(null);
        if (row == null) {
            return MemoireSynaptiqueResponse.builder()
                    .atelierId(atelier.getId())
                    .atelierNom(atelier.getNom())
                    .overview("(mémoire non initialisée — sera créée au prochain événement)")
                    .recentFacts(List.of())
                    .build();
        }
        return MemoireSynaptiqueResponse.builder()
                .atelierId(atelier.getId())
                .atelierNom(atelier.getNom())
                .overview(row.getOverview())
                .recentFacts(readFacts(row.getFactsJson()).stream().limit(15).toList())
                .lastEventType(row.getLastEventType())
                .lastEventAt(row.getLastEventAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    @Transactional
    public MemoireSynaptiqueResponse rebuildCurrent() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        AtelierMemoireSynaptique row = ensureRow(atelier);
        row.setOverview(buildOverview(atelier.getId(), atelier.getNom()));
        row.setUpdatedAt(LocalDateTime.now());
        memoireRepository.save(row);
        return currentForApi();
    }

    private void recordFact(Long atelierId, String type, String fact) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        if (!atelierId.equals(atelier.getId())) {
            log.debug("Événement mémoire ignoré (atelier event={} courant={})",
                    atelierId, atelier.getId());
            return;
        }
        AtelierMemoireSynaptique row = ensureRow(atelier);
        List<String> facts = readFacts(row.getFactsJson());
        String stamped = LocalDateTime.now().format(TS) + " [" + safeType(type) + "] "
                + truncate(fact == null ? "" : fact.trim(), MAX_FACT_CHARS);
        facts.add(0, stamped);
        if (facts.size() > MAX_FACTS) {
            facts = new ArrayList<>(facts.subList(0, MAX_FACTS));
        }
        row.setFactsJson(writeFacts(facts));
        row.setLastEventType(safeType(type));
        row.setLastEventAt(LocalDateTime.now());
        row.setOverview(buildOverview(atelierId, atelier.getNom()));
        row.setUpdatedAt(LocalDateTime.now());
        memoireRepository.save(row);
        log.debug("Mémoire synaptique atelier={} event={}", atelierId, type);
    }

    private AtelierMemoireSynaptique ensureAndRefresh(Atelier atelier) {
        AtelierMemoireSynaptique row = ensureRow(atelier);
        // Rafraîchir le snapshot chiffré à chaque lecture chat (léger)
        row.setOverview(buildOverview(atelier.getId(), atelier.getNom()));
        row.setUpdatedAt(LocalDateTime.now());
        return memoireRepository.save(row);
    }

    private AtelierMemoireSynaptique ensureRow(Atelier atelier) {
        return memoireRepository.findByAtelierId(atelier.getId()).orElseGet(() -> {
            AtelierMemoireSynaptique created = AtelierMemoireSynaptique.builder()
                    .atelier(atelier)
                    .overview(buildOverview(atelier.getId(), atelier.getNom()))
                    .factsJson("[]")
                    .updatedAt(LocalDateTime.now())
                    .build();
            return memoireRepository.save(created);
        });
    }

    private String buildOverview(Long atelierId, String atelierNom) {
        long devices = deviceRepository.countByAtelierId(atelierId);
        long obsolete = deviceRepository.countByAtelierIdAndObsoleteTrue(atelierId);
        long zeroStock = deviceRepository.countZeroStockByAtelierId(atelierId);
        long mas = masRepository.countByAtelierId(atelierId);
        long sfm = sfmRepository.countByAtelierId(atelierId);
        long todosOpen = todoTacheRepository.countByAtelierIdAndStatutIn(
                atelierId, EnumSet.of(TodoTacheStatut.OPEN, TodoTacheStatut.IN_PROGRESS));
        long cmdPending = commandeRepository.countByAtelierIdAndStatusIn(
                atelierId, List.of(OrderStatuses.PENDING, OrderStatuses.SENT));
        long cmdValidated = commandeRepository.countByAtelierIdAndStatusIn(
                atelierId, List.of(OrderStatuses.VALIDATED));
        long bons = interventionRepository.countByAtelierId(atelierId);
        long its = interventionTechniqueRepository.countByAtelierId(atelierId);
        long arretsOuverts = arretMaintenanceRepository.countByAtelierIdAndDateHeureRepriseIsNull(atelierId);

        StringBuilder sb = new StringBuilder();
        sb.append("Situation atelier « ").append(atelierNom == null ? atelierId : atelierNom).append(" ».\n");
        sb.append("Parc : ").append(devices).append(" pièces (")
                .append(obsolete).append(" obsolètes, ")
                .append(zeroStock).append(" en rupture de stock), ")
                .append(mas).append(" MAS, ")
                .append(sfm).append(" SFM.\n");
        sb.append("Activité : ").append(todosOpen).append(" tâche(s) À faire actives, ")
                .append(cmdPending).append(" commande(s) en attente, ")
                .append(cmdValidated).append(" validée(s) non réceptionnée(s), ")
                .append(arretsOuverts).append(" arrêt(s) maintenance ouverts.\n");
        sb.append("Historique interventions : ").append(its).append(" technique(s), ")
                .append(bons).append(" bon(s) pièces.\n");
        sb.append("Utilise ces chiffres et les faits récents pour répondre de façon concrète sur cet atelier.");
        String out = sb.toString();
        return out.length() > MAX_OVERVIEW_CHARS ? out.substring(0, MAX_OVERVIEW_CHARS) : out;
    }

    private List<String> filterRelevantFacts(List<String> facts, String question) {
        if (facts.isEmpty()) {
            return List.of();
        }
        if (question == null || question.isBlank()) {
            return facts.stream().limit(8).toList();
        }
        String q = question.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(q);
        List<String> matched = facts.stream()
                .filter(f -> {
                    String lower = f.toLowerCase(Locale.ROOT);
                    return tokens.stream().anyMatch(lower::contains);
                })
                .limit(10)
                .collect(Collectors.toCollection(ArrayList::new));
        if (matched.isEmpty()) {
            return facts.stream().limit(6).toList();
        }
        return matched;
    }

    private static List<String> tokenize(String q) {
        String[] parts = q.split("[^a-zA-Z0-9àâäéèêëïîôùûüçÀÂÄÉÈÊËÏÎÔÙÛÜÇ_-]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p != null && p.length() >= 3) {
                out.add(p.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private List<String> readFacts(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }

    private String writeFacts(List<String> facts) {
        try {
            return objectMapper.writeValueAsString(facts);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static String safeType(String type) {
        if (type == null || type.isBlank()) {
            return "EVENT";
        }
        return type.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
