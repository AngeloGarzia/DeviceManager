package com.devicemanager.service;

import com.devicemanager.dto.TodoModeleRequest;
import com.devicemanager.dto.TodoModeleResponse;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.TodoModele;
import com.devicemanager.entity.User;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoModeleRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Modèles de tâches simples — éditables par tout utilisateur authentifié de l'atelier.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TodoModeleService {

    private final TodoModeleRepository todoModeleRepository;
    private final MasRepository masRepository;
    private final UserRepository userRepository;
    private final AtelierService atelierService;
    private final TodoService todoService;

    @Transactional(readOnly = true)
    public List<TodoModeleResponse> list() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return todoModeleRepository.findAllByAtelierIdOrderByPosition(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoModeleResponse create(TodoModeleRequest request, String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        User actor = requireUser(username);
        TodoModele saved = todoModeleRepository.save(applyRequest(new TodoModele(), request, atelier, actor));
        log.info("Création modèle todo id={} titre={} par={}", saved.getId(), saved.getTitre(), username);
        return toResponse(saved);
    }

    public TodoModeleResponse update(Long id, TodoModeleRequest request, String username) {
        TodoModele entity = getEntity(id);
        applyRequest(entity, request, entity.getAtelier(), requireUser(username));
        TodoModele saved = todoModeleRepository.save(entity);
        log.info("Modification modèle todo id={} par={}", id, username);
        return toResponse(saved);
    }

    public void delete(Long id, String username) {
        TodoModele entity = getEntity(id);
        todoModeleRepository.delete(entity);
        log.info("Suppression modèle todo id={} par={}", id, username);
    }

    /** Crée une tâche ponctuelle à partir d'un modèle. */
    public TodoTacheResponse utiliser(Long id, String username) {
        TodoModele modele = getEntity(id);
        TodoTacheRequest request = new TodoTacheRequest();
        request.setTitre(modele.getTitre());
        request.setDescription(modele.getDescription());
        request.setSeverite(modele.getSeverite());
        request.setMasId(modele.getMas() != null ? modele.getMas().getId() : null);
        return todoService.create(request, username);
    }

    private TodoModele applyRequest(TodoModele entity, TodoModeleRequest request, Atelier atelier, User actor) {
        entity.setAtelier(atelier);
        entity.setTitre(requireTitre(request.getTitre()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSeverite(normalizeSeverite(request.getSeverite()));
        entity.setMas(resolveMas(request.getMasId(), atelier.getId()));
        entity.setPosition(request.getPosition() != null ? request.getPosition() : entity.getPosition());
        if (entity.getId() == null) {
            entity.setCreatedByUsername(actor.getUsername());
        }
        return entity;
    }

    private TodoModele getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return todoModeleRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable"));
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

    private TodoModeleResponse toResponse(TodoModele m) {
        Mas mas = m.getMas();
        return TodoModeleResponse.builder()
                .id(m.getId())
                .titre(m.getTitre())
                .description(m.getDescription())
                .severite(m.getSeverite())
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .position(m.getPosition())
                .createdByUsername(m.getCreatedByUsername())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
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
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
