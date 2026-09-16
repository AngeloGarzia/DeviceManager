package com.devicemanager.service;

import com.devicemanager.dto.TodoRecurrenceRequest;
import com.devicemanager.dto.TodoRecurrenceResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.TodoRecurrence;
import com.devicemanager.entity.TodoRecurrenceFrequence;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.entity.User;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoRecurrenceRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Règles de tâches récurrentes : CRUD + génération lazy des occurrences {@link TodoTache}.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TodoRecurrenceService {

    private static final int MAX_CATCH_UP = 366;
    private static final DateTimeFormatter OCCURRENCE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TodoRecurrenceRepository todoRecurrenceRepository;
    private final TodoTacheRepository todoTacheRepository;
    private final MasRepository masRepository;
    private final UserRepository userRepository;
    private final AtelierService atelierService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TodoRecurrenceResponse> list() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return todoRecurrenceRepository.findAllByAtelierId(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoRecurrenceResponse create(TodoRecurrenceRequest request, String username) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        User actor = requireUser(username);
        TodoRecurrence entity = applyRequest(new TodoRecurrence(), request, atelier);
        entity.setCreatedByUsername(actor.getUsername());
        entity.setProchaineEcheance(computeFirstDue(entity));
        TodoRecurrence saved = todoRecurrenceRepository.save(entity);
        log.info("Création règle todo récurrente id={} titre={} par={}",
                saved.getId(), saved.getTitre(), username);
        return toResponse(saved);
    }

    public TodoRecurrenceResponse update(Long id, TodoRecurrenceRequest request, String username) {
        TodoRecurrence entity = getEntity(id);
        applyRequest(entity, request, entity.getAtelier());
        // Recalcule le curseur si la règle est (re)activée et le prochain créneau est absent / passé.
        LocalDateTime now = LocalDateTime.now(clock);
        if (entity.isActive()) {
            if (entity.getProchaineEcheance() == null || entity.getProchaineEcheance().isBefore(now.minusYears(1))) {
                entity.setProchaineEcheance(computeFirstDue(entity));
            }
        }
        TodoRecurrence saved = todoRecurrenceRepository.save(entity);
        log.info("Modification règle todo récurrente id={} par={}", id, username);
        return toResponse(saved);
    }

    public TodoRecurrenceResponse setActive(Long id, boolean active, String username) {
        TodoRecurrence entity = getEntity(id);
        entity.setActive(active);
        if (active && entity.getProchaineEcheance() == null) {
            entity.setProchaineEcheance(computeFirstDue(entity));
        }
        TodoRecurrence saved = todoRecurrenceRepository.save(entity);
        log.info("Règle todo récurrente id={} active={} par={}", id, active, username);
        return toResponse(saved);
    }

    public void delete(Long id, String username) {
        TodoRecurrence entity = getEntity(id);
        todoRecurrenceRepository.delete(entity);
        log.info("Suppression règle todo récurrente id={} par={}", id, username);
    }

    /**
     * Crée les occurrences dues ({@code due_at <= now}) pour l'atelier courant.
     */
    public void generateDueOccurrences() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        generateDueOccurrences(atelier);
    }

    public void generateDueOccurrences(Atelier atelier) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TodoRecurrence> rules = todoRecurrenceRepository.findActiveByAtelierId(atelier.getId());
        for (TodoRecurrence rule : rules) {
            LocalDateTime next = rule.getProchaineEcheance();
            if (next == null) {
                next = computeFirstDue(rule);
            }
            int guard = 0;
            while (next != null && !next.isAfter(now) && guard++ < MAX_CATCH_UP) {
                if (rule.getDateFin() != null && next.toLocalDate().isAfter(rule.getDateFin())) {
                    next = null;
                    break;
                }
                String key = occurrenceKey(next);
                if (!todoTacheRepository.existsByRecurrenceIdAndOccurrenceKey(rule.getId(), key)) {
                    TodoTache occurrence = TodoTache.builder()
                            .atelier(atelier)
                            .titre(rule.getTitre())
                            .description(rule.getDescription())
                            .statut(TodoTacheStatut.OPEN)
                            .severite(rule.getSeverite())
                            .createdByUsername(rule.getCreatedByUsername())
                            .createdByDisplayName(null)
                            .mas(rule.getMas())
                            .recurrence(rule)
                            .dueAt(next)
                            .occurrenceKey(key)
                            .build();
                    todoTacheRepository.save(occurrence);
                    log.info("Occurrence todo générée recurrenceId={} key={} dueAt={}",
                            rule.getId(), key, next);
                }
                next = advance(rule, next);
            }
            rule.setProchaineEcheance(next);
            todoRecurrenceRepository.save(rule);
        }
    }

    private TodoRecurrence applyRequest(TodoRecurrence entity, TodoRecurrenceRequest request, Atelier atelier) {
        entity.setAtelier(atelier);
        entity.setTitre(requireTitre(request.getTitre()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSeverite(normalizeSeverite(request.getSeverite()));
        entity.setMas(resolveMas(request.getMasId(), atelier.getId()));
        TodoRecurrenceFrequence frequence = parseFrequence(request.getFrequence());
        entity.setFrequence(frequence);
        entity.setIntervalDays(request.getIntervalDays());
        entity.setJourSemaine(request.getJourSemaine());
        entity.setJourMois(request.getJourMois());
        entity.setHeureDue(request.getHeureDue() != null ? request.getHeureDue() : LocalTime.of(8, 0));
        if (request.getDateDebut() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début est obligatoire");
        }
        entity.setDateDebut(request.getDateDebut());
        entity.setDateFin(request.getDateFin());
        if (request.getDateFin() != null && request.getDateFin().isBefore(request.getDateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin doit être postérieure à la date de début");
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        } else if (entity.getId() == null) {
            entity.setActive(true);
        }
        validateFrequenceFields(entity);
        return entity;
    }

    private static void validateFrequenceFields(TodoRecurrence entity) {
        switch (entity.getFrequence()) {
            case INTERVAL_DAYS -> {
                if (entity.getIntervalDays() == null || entity.getIntervalDays() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "intervalDays obligatoire pour INTERVAL_DAYS (min 1)");
                }
            }
            case WEEKLY -> {
                if (entity.getJourSemaine() == null || entity.getJourSemaine() < 1 || entity.getJourSemaine() > 7) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "jourSemaine obligatoire pour WEEKLY (1=lundi … 7=dimanche)");
                }
            }
            case MONTHLY -> {
                if (entity.getJourMois() == null || entity.getJourMois() < 1 || entity.getJourMois() > 28) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "jourMois obligatoire pour MONTHLY (1–28)");
                }
            }
            case DAILY -> { /* rien */ }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fréquence non supportée : " + entity.getFrequence());
        }
    }

    LocalDateTime computeFirstDue(TodoRecurrence rule) {
        LocalTime time = rule.getHeureDue() != null ? rule.getHeureDue() : LocalTime.of(8, 0);
        LocalDate start = rule.getDateDebut();
        return switch (rule.getFrequence()) {
            case DAILY, INTERVAL_DAYS -> LocalDateTime.of(start, time);
            case WEEKLY -> {
                DayOfWeek wanted = DayOfWeek.of(rule.getJourSemaine());
                LocalDate aligned = start.getDayOfWeek() == wanted
                        ? start
                        : start.with(TemporalAdjusters.nextOrSame(wanted));
                yield LocalDateTime.of(aligned, time);
            }
            case MONTHLY -> {
                int day = rule.getJourMois();
                LocalDate candidate;
                if (start.getDayOfMonth() <= day) {
                    candidate = start.withDayOfMonth(day);
                } else {
                    candidate = start.plusMonths(1).withDayOfMonth(day);
                }
                yield LocalDateTime.of(candidate, time);
            }
        };
    }

    LocalDateTime advance(TodoRecurrence rule, LocalDateTime from) {
        LocalTime time = rule.getHeureDue() != null ? rule.getHeureDue() : LocalTime.of(8, 0);
        return switch (rule.getFrequence()) {
            case DAILY -> LocalDateTime.of(from.toLocalDate().plusDays(1), time);
            case INTERVAL_DAYS -> LocalDateTime.of(
                    from.toLocalDate().plusDays(rule.getIntervalDays()), time);
            case WEEKLY -> LocalDateTime.of(from.toLocalDate().plusWeeks(1), time);
            case MONTHLY -> {
                LocalDate nextMonth = from.toLocalDate().plusMonths(1);
                int day = Math.min(rule.getJourMois(), nextMonth.lengthOfMonth());
                yield LocalDateTime.of(nextMonth.withDayOfMonth(day), time);
            }
        };
    }

    static String occurrenceKey(LocalDateTime dueAt) {
        return dueAt.toLocalDate().format(OCCURRENCE_KEY);
    }

    private TodoRecurrence getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return todoRecurrenceRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle introuvable"));
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

    private static TodoRecurrenceFrequence parseFrequence(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fréquence obligatoire");
        }
        try {
            return TodoRecurrenceFrequence.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fréquence inconnue : " + raw);
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

    private TodoRecurrenceResponse toResponse(TodoRecurrence r) {
        Mas mas = r.getMas();
        return TodoRecurrenceResponse.builder()
                .id(r.getId())
                .titre(r.getTitre())
                .description(r.getDescription())
                .severite(r.getSeverite())
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .frequence(r.getFrequence().name())
                .intervalDays(r.getIntervalDays())
                .jourSemaine(r.getJourSemaine())
                .jourMois(r.getJourMois())
                .heureDue(r.getHeureDue())
                .dateDebut(r.getDateDebut())
                .dateFin(r.getDateFin())
                .active(r.isActive())
                .prochaineEcheance(r.getProchaineEcheance())
                .createdByUsername(r.getCreatedByUsername())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
