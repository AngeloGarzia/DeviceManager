package com.devicemanager.service;

import com.devicemanager.dto.TodoListResponse;
import com.devicemanager.dto.TodoTacheLinkRequest;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.dto.TodoTacheStatusRequest;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionTechnique;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.entity.User;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tâches « À faire » persistées : création libre, cycle de vie, rattachement d'interventions.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TodoService {

    private static final int MIN_SIGNATURE_LENGTH = 80;

    private static final Set<TodoTacheStatut> ACTIVE = EnumSet.of(
            TodoTacheStatut.OPEN, TodoTacheStatut.IN_PROGRESS);

    private final TodoTacheRepository todoTacheRepository;
    private final MasRepository masRepository;
    private final InterventionTechniqueRepository interventionTechniqueRepository;
    private final InterventionRepository interventionRepository;
    private final UserRepository userRepository;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public TodoListResponse listPending() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<TodoTacheResponse> items = todoTacheRepository
                .findByAtelierIdAndStatutIn(atelierId, ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
        return TodoListResponse.builder()
                .count(items.size())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public TodoListResponse listAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<TodoTacheResponse> items = todoTacheRepository
                .findByAtelierIdAndStatutIn(atelierId, EnumSet.allOf(TodoTacheStatut.class))
                .stream()
                .map(this::toResponse)
                .toList();
        return TodoListResponse.builder()
                .count(items.size())
                .items(items)
                .build();
    }

    public TodoTacheResponse create(TodoTacheRequest request, String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        User actor = requireUser(username);
        String titre = requireTitre(request.getTitre());
        String description = trimToNull(request.getDescription());
        String severite = normalizeSeverite(request.getSeverite());
        Mas mas = resolveMas(request.getMasId(), atelier.getId());

        TodoTache saved = todoTacheRepository.save(TodoTache.builder()
                .atelier(atelier)
                .titre(titre)
                .description(description)
                .statut(TodoTacheStatut.OPEN)
                .severite(severite)
                .createdByUsername(actor.getUsername())
                .createdByDisplayName(displayName(actor))
                .mas(mas)
                .build());
        log.info("Création en base — Tâche À faire id={} titre={} par={}",
                saved.getId(), saved.getTitre(), username);
        return toResponse(saved);
    }

    public TodoTacheResponse update(Long id, TodoTacheRequest request, String username) {
        TodoTache entity = getEntity(id);
        if (!entity.getStatut().isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de modifier une tâche terminée ou annulée");
        }
        entity.setTitre(requireTitre(request.getTitre()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSeverite(normalizeSeverite(request.getSeverite()));
        entity.setMas(resolveMas(request.getMasId(), entity.getAtelier().getId()));
        TodoTache saved = todoTacheRepository.save(entity);
        log.info("Modification en base — Tâche À faire id={} par={}", id, username);
        return toResponse(saved);
    }

    public TodoTacheResponse changeStatus(Long id, TodoTacheStatusRequest request, String username) {
        TodoTache entity = getEntity(id);
        TodoTacheStatut next = parseStatut(request.getStatut());
        TodoTacheStatut current = entity.getStatut();
        if (current == next) {
            return toResponse(entity);
        }
        validateTransition(current, next);
        User actor = requireUser(username);

        entity.setStatut(next);
        if (next == TodoTacheStatut.DONE) {
            validateDrawnSignature(request.getSignatureCloture());
            LocalDateTime clotureAt = request.getDateHeureCloture() != null
                    ? request.getDateHeureCloture()
                    : LocalDateTime.now();
            String signataire = trimToNull(request.getSignataireClotureNom());
            if (signataire == null) {
                signataire = displayName(actor);
            }
            entity.setCompletedAt(clotureAt);
            entity.setCompletedByUsername(actor.getUsername());
            entity.setCompletedByDisplayName(displayName(actor));
            entity.setSignatureCloture(request.getSignatureCloture().trim());
            entity.setSignataireClotureNom(signataire);
            entity.setCommentaireCloture(trimToNull(request.getCommentaireCloture()));
        } else if (next == TodoTacheStatut.CANCELLED) {
            entity.setCompletedAt(LocalDateTime.now());
            entity.setCompletedByUsername(actor.getUsername());
            entity.setCompletedByDisplayName(displayName(actor));
            entity.setSignatureCloture(null);
            entity.setSignataireClotureNom(null);
            entity.setCommentaireCloture(null);
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedByUsername(null);
            entity.setCompletedByDisplayName(null);
            entity.setSignatureCloture(null);
            entity.setSignataireClotureNom(null);
            entity.setCommentaireCloture(null);
        }
        TodoTache saved = todoTacheRepository.save(entity);
        log.info("Statut tâche À faire id={} {} → {} par={}", id, current, next, username);
        return toResponse(saved);
    }

    public TodoTacheResponse linkIntervention(Long id, TodoTacheLinkRequest request, String username) {
        TodoTache entity = getEntity(id);
        if (!entity.getStatut().isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de rattacher une intervention à une tâche clôturée");
        }
        Long atelierId = entity.getAtelier().getId();

        if (Boolean.TRUE.equals(request.getClearTechnique())) {
            entity.setInterventionTechnique(null);
        } else if (request.getInterventionTechniqueId() != null) {
            InterventionTechnique it = interventionTechniqueRepository
                    .findByIdAndAtelierId(request.getInterventionTechniqueId(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Intervention technique introuvable"));
            entity.setInterventionTechnique(it);
            if (entity.getMas() == null && it.getMas() != null) {
                entity.setMas(it.getMas());
            }
        }

        if (Boolean.TRUE.equals(request.getClearBon())) {
            entity.setIntervention(null);
        } else if (request.getInterventionId() != null) {
            Intervention bon = interventionRepository.findById(request.getInterventionId())
                    .filter(i -> i.getAtelier() != null && atelierId.equals(i.getAtelier().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Bon d'intervention introuvable"));
            entity.setIntervention(bon);
        }

        TodoTache saved = todoTacheRepository.save(entity);
        log.info("Rattachement intervention — Tâche id={} par={}", id, username);
        return toResponse(saved);
    }

    public void delete(Long id, String username) {
        TodoTache entity = getEntity(id);
        todoTacheRepository.delete(entity);
        log.info("Suppression en base — Tâche À faire id={} par={}", id, username);
    }

    private TodoTache getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return todoTacheRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche introuvable"));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private Mas resolveMas(Long masId, Long atelierId) {
        if (masId == null) {
            return null;
        }
        return masRepository.findByIdAndAtelierId(masId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAS introuvable"));
    }

    private static void validateTransition(TodoTacheStatut from, TodoTacheStatut to) {
        boolean ok = switch (from) {
            case OPEN -> to == TodoTacheStatut.IN_PROGRESS
                    || to == TodoTacheStatut.DONE
                    || to == TodoTacheStatut.CANCELLED;
            case IN_PROGRESS -> to == TodoTacheStatut.OPEN
                    || to == TodoTacheStatut.DONE
                    || to == TodoTacheStatut.CANCELLED;
            case DONE, CANCELLED -> to == TodoTacheStatut.OPEN || to == TodoTacheStatut.IN_PROGRESS;
        };
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transition de statut invalide : " + from + " → " + to);
        }
    }

    private static TodoTacheStatut parseStatut(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut obligatoire");
        }
        try {
            return TodoTacheStatut.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu : " + raw);
        }
    }

    private static String requireTitre(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre est obligatoire");
        }
        String titre = raw.trim();
        if (titre.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titre trop long (max 200)");
        }
        return titre;
    }

    private static String normalizeSeverite(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MEDIUM";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sévérité invalide");
        }
        return s;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String displayName(User user) {
        String prenom = user.getPrenom() != null ? user.getPrenom().trim() : "";
        String nom = user.getNom() != null ? user.getNom().trim() : "";
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    private static void validateDrawnSignature(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature de clôture est obligatoire.");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("data:image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature de clôture doit être une image dessinée.");
        }
        if (trimmed.length() < MIN_SIGNATURE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La signature de clôture est vide ou trop courte.");
        }
    }

    private TodoTacheResponse toResponse(TodoTache t) {
        Mas mas = t.getMas();
        InterventionTechnique it = t.getInterventionTechnique();
        Intervention bon = t.getIntervention();
        String itLabel = null;
        if (it != null) {
            String masNum = it.getMas() != null ? it.getMas().getNumero() : null;
            itLabel = "IT #" + it.getId()
                    + (masNum != null ? " — MAS " + masNum : "")
                    + (it.getMotif() != null ? " — " + abbreviate(it.getMotif(), 60) : "");
        }
        String responsableCreation = t.getCreatedByDisplayName() != null && !t.getCreatedByDisplayName().isBlank()
                ? t.getCreatedByDisplayName()
                : t.getCreatedByUsername();
        String responsableCloture = t.getCompletedByDisplayName() != null && !t.getCompletedByDisplayName().isBlank()
                ? t.getCompletedByDisplayName()
                : (t.getSignataireClotureNom() != null && !t.getSignataireClotureNom().isBlank()
                        ? t.getSignataireClotureNom()
                        : t.getCompletedByUsername());
        return TodoTacheResponse.builder()
                .id(t.getId())
                .titre(t.getTitre())
                .description(t.getDescription())
                .statut(t.getStatut().name())
                .severite(t.getSeverite())
                .createdByUsername(t.getCreatedByUsername())
                .createdByDisplayName(t.getCreatedByDisplayName())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .completedAt(t.getCompletedAt())
                .completedByUsername(t.getCompletedByUsername())
                .completedByDisplayName(t.getCompletedByDisplayName())
                .signatureCloture(t.getSignatureCloture())
                .signataireClotureNom(t.getSignataireClotureNom())
                .commentaireCloture(t.getCommentaireCloture())
                .dateCreation(t.getCreatedAt())
                .responsableCreation(responsableCreation)
                .dateCloture(t.getCompletedAt())
                .responsableCloture(responsableCloture)
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .interventionTechniqueId(it != null ? it.getId() : null)
                .interventionTechniqueLabel(itLabel)
                .interventionId(bon != null ? bon.getId() : null)
                .interventionNumero(bon != null ? bon.getNumero() : null)
                .type("USER")
                .severity(t.getSeverite())
                .title(t.getTitre())
                .link("/devices")
                .relatedId(t.getId())
                .since(t.getCreatedAt())
                .build();
    }

    private static String abbreviate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
