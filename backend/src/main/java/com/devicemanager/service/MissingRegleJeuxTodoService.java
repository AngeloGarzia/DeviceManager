package com.devicemanager.service;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoTacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Crée / met à jour / annule une tâche simple prioritaire lorsque des MAS
 * de l'atelier n'ont aucune règle de jeux rattachée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MissingRegleJeuxTodoService {

    static final String AUTO_MARKER = "[AUTO:REGLES_JEUX_MANQUANTES]";
    static final String SYSTEM_USERNAME = "system";
    static final String SYSTEM_DISPLAY_NAME = "DeviceManager";
    private static final int DESCRIPTION_MAX = 2000;
    private static final Set<TodoTacheStatut> ACTIVE = EnumSet.of(
            TodoTacheStatut.OPEN, TodoTacheStatut.IN_PROGRESS);

    private final MasRepository masRepository;
    private final TodoTacheRepository todoTacheRepository;
    private final AtelierService atelierService;
    private final AtelierMemoirePublisher atelierMemoirePublisher;

    /** Synchronise la tâche automatique pour l'atelier courant. */
    public void syncForCurrentAtelier() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        sync(atelier);
    }

    /** Synchronise la tâche automatique pour un atelier donné. */
    public void sync(Atelier atelier) {
        if (atelier == null || atelier.getId() == null) {
            return;
        }
        List<Mas> missing = masRepository.findAllByAtelierId(atelier.getId()).stream()
                .filter(m -> m.getReglesJeux() == null || m.getReglesJeux().isEmpty())
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        nullToEmpty(a.getNumero()), nullToEmpty(b.getNumero())))
                .toList();

        List<TodoTache> existing = todoTacheRepository
                .findByAtelierIdAndStatutInAndDescriptionContaining(atelier.getId(), ACTIVE, AUTO_MARKER);

        if (missing.isEmpty()) {
            for (TodoTache todo : existing) {
                cancelSystemTodo(todo);
            }
            return;
        }

        String titre = buildTitre(missing.size());
        String description = buildDescription(missing);

        if (existing.isEmpty()) {
            TodoTache created = TodoTache.builder()
                    .atelier(atelier)
                    .titre(titre)
                    .description(description)
                    .statut(TodoTacheStatut.OPEN)
                    .severite("HIGH")
                    .createdByUsername(SYSTEM_USERNAME)
                    .createdByDisplayName(SYSTEM_DISPLAY_NAME)
                    .build();
            todoTacheRepository.save(created);
            log.info("Tâche auto règles manquantes créée atelier={} count={}", atelier.getId(), missing.size());
            atelierMemoirePublisher.publish("TODO_CREATED",
                    "Tâche automatique : " + titre);
            return;
        }

        TodoTache primary = existing.getFirst();
        boolean changed = !titre.equals(primary.getTitre())
                || !description.equals(nullToEmpty(primary.getDescription()));
        if (changed) {
            primary.setTitre(titre);
            primary.setDescription(description);
            primary.setSeverite("HIGH");
            if (primary.getStatut() != TodoTacheStatut.OPEN
                    && primary.getStatut() != TodoTacheStatut.IN_PROGRESS) {
                primary.setStatut(TodoTacheStatut.OPEN);
            }
            todoTacheRepository.save(primary);
            log.info("Tâche auto règles manquantes mise à jour id={} count={}", primary.getId(), missing.size());
        }
        for (int i = 1; i < existing.size(); i++) {
            cancelSystemTodo(existing.get(i));
        }
    }

    private void cancelSystemTodo(TodoTache todo) {
        todo.setStatut(TodoTacheStatut.CANCELLED);
        todo.setCompletedAt(LocalDateTime.now());
        todo.setCompletedByUsername(SYSTEM_USERNAME);
        todo.setCompletedByDisplayName(SYSTEM_DISPLAY_NAME);
        todo.setCommentaireCloture("Toutes les MAS ont désormais une règle de jeux.");
        todoTacheRepository.save(todo);
        log.info("Tâche auto règles manquantes annulée id={}", todo.getId());
    }

    static String buildTitre(int count) {
        if (count <= 1) {
            return "Il manque 1 règle de jeux";
        }
        return "Il manque " + count + " règles de jeux";
    }

    static String buildDescription(List<Mas> missing) {
        String header = AUTO_MARKER + "\nMAS concernées (" + missing.size() + ") :\n";
        StringBuilder body = new StringBuilder(header);
        for (Mas mas : missing) {
            String line = "- " + nullToEmpty(mas.getNumero()) + "\n";
            if (body.length() + line.length() > DESCRIPTION_MAX) {
                body.append("- …\n");
                break;
            }
            body.append(line);
        }
        String text = body.toString().stripTrailing();
        if (text.length() > DESCRIPTION_MAX) {
            return text.substring(0, DESCRIPTION_MAX);
        }
        return text;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
