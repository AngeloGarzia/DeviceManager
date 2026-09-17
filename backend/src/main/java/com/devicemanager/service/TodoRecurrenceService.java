package com.devicemanager.service;

import com.devicemanager.dto.TodoRecurrenceRequest;
import com.devicemanager.dto.TodoRecurrenceResponse;
import com.devicemanager.dto.TodoWeekCalendarResponse;
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
import com.devicemanager.security.Roles;
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
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    /**
     * Une occurrence hebdo/mensuelle peut être générée et clôturée jusqu'à N jours avant son échéance.
     * La prochaine période (semaine/mois suivant) reste planifiée via {@link #advance}.
     */
    public static final int EARLY_COMPLETION_DAYS = 7;
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
        requireAdmin(username);
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
        requireAdmin(username);
        TodoRecurrence entity = getEntity(id);
        applyRequest(entity, request, entity.getAtelier());
        recomputeProchaineEcheance(entity);
        TodoRecurrence saved = todoRecurrenceRepository.save(entity);
        log.info("Modification règle todo récurrente id={} par={}", id, username);
        return toResponse(saved);
    }

    public TodoRecurrenceResponse setActive(Long id, boolean active, String username) {
        requireAdmin(username);
        TodoRecurrence entity = getEntity(id);
        entity.setActive(active);
        if (active) {
            recomputeProchaineEcheance(entity);
        }
        TodoRecurrence saved = todoRecurrenceRepository.save(entity);
        log.info("Règle todo récurrente id={} active={} par={}", id, active, username);
        return toResponse(saved);
    }

    public void delete(Long id, String username) {
        requireAdmin(username);
        TodoRecurrence entity = getEntity(id);
        todoRecurrenceRepository.delete(entity);
        log.info("Suppression règle todo récurrente id={} par={}", id, username);
    }

    /**
     * Recalcule {@code prochaineEcheance} : premier créneau ≥ maintenant (hors dateFin).
     */
    void recomputeProchaineEcheance(TodoRecurrence entity) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime next = computeFirstDue(entity);
        int guard = 0;
        while (next != null && next.isBefore(now) && guard++ < MAX_CATCH_UP) {
            if (entity.getDateFin() != null && next.toLocalDate().isAfter(entity.getDateFin())) {
                next = null;
                break;
            }
            next = advance(entity, next);
        }
        if (next != null && entity.getDateFin() != null && next.toLocalDate().isAfter(entity.getDateFin())) {
            next = null;
        }
        entity.setProchaineEcheance(next);
    }

    /**
     * Calendrier de la semaine ISO courante : pastilles pour tâches récurrentes dues.
     */
    public TodoWeekCalendarResponse weekCalendar() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        generateDueOccurrences(atelier);

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        EnumSet<TodoTacheStatut> active = EnumSet.of(TodoTacheStatut.OPEN, TodoTacheStatut.IN_PROGRESS);
        List<TodoTache> occurrences = todoTacheRepository.findRecurringActiveDueBetween(
                atelier.getId(),
                active,
                weekStart.atStartOfDay(),
                weekEnd.plusDays(1).atStartOfDay());

        Map<LocalDate, Integer> recurringByDay = new HashMap<>();
        Map<LocalDate, Integer> overdueByDay = new HashMap<>();
        Set<String> existingKeys = new HashSet<>();
        for (TodoTache t : occurrences) {
            if (t.getDueAt() == null || t.getRecurrence() == null) {
                continue;
            }
            LocalDate day = t.getDueAt().toLocalDate();
            recurringByDay.merge(day, 1, Integer::sum);
            if (t.getDueAt().isBefore(now)) {
                overdueByDay.merge(day, 1, Integer::sum);
            }
            existingKeys.add(t.getRecurrence().getId() + "|" + occurrenceKey(t.getDueAt()));
        }

        List<TodoRecurrence> rules = todoRecurrenceRepository.findActiveByAtelierId(atelier.getId());
        for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
            // Jours futurs : compléter avec les échéances virtuelles (pas encore matérialisées).
            if (day.isAfter(today)) {
                for (TodoRecurrence rule : rules) {
                    if (!isDueOn(rule, day)) {
                        continue;
                    }
                    String key = rule.getId() + "|" + day.format(OCCURRENCE_KEY);
                    if (existingKeys.contains(key)) {
                        continue;
                    }
                    recurringByDay.merge(day, 1, Integer::sum);
                    existingKeys.add(key);
                }
            }
        }

        List<TodoWeekCalendarResponse.Day> days = new ArrayList<>(7);
        for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
            int recurring = recurringByDay.getOrDefault(day, 0);
            int overdue = overdueByDay.getOrDefault(day, 0);
            days.add(TodoWeekCalendarResponse.Day.builder()
                    .date(day)
                    .dayOfWeek(day.getDayOfWeek().getValue())
                    .label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH))
                    .recurringCount(recurring)
                    .overdueCount(overdue)
                    .build());
        }

        return TodoWeekCalendarResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .days(days)
                .build();
    }

    /**
     * Indique si la règle produit une échéance ce jour-là (sans créer d'occurrence).
     */
    boolean isDueOn(TodoRecurrence rule, LocalDate day) {
        if (rule.getDateDebut() != null && day.isBefore(rule.getDateDebut())) {
            return false;
        }
        if (rule.getDateFin() != null && day.isAfter(rule.getDateFin())) {
            return false;
        }
        return switch (rule.getFrequence()) {
            case DAILY -> true;
            case WEEKLY -> {
                if (rule.getJourSemaine() == null) {
                    yield false;
                }
                DayOfWeek wanted = DayOfWeek.of(rule.getJourSemaine());
                LocalDate first = rule.getDateDebut().with(TemporalAdjusters.nextOrSame(wanted));
                yield !day.isBefore(first) && day.getDayOfWeek() == wanted;
            }
            case MONTHLY -> {
                if (rule.getJourMois() == null) {
                    yield false;
                }
                LocalDateTime firstDue = computeFirstDue(rule);
                yield !day.isBefore(firstDue.toLocalDate()) && day.getDayOfMonth() == rule.getJourMois();
            }
            case INTERVAL_DAYS -> {
                if (rule.getIntervalDays() == null || rule.getIntervalDays() < 1) {
                    yield false;
                }
                long days = ChronoUnit.DAYS.between(rule.getDateDebut(), day);
                yield days >= 0 && days % rule.getIntervalDays() == 0;
            }
        };
    }

    /**
     * Crée les occurrences dues ({@code due_at <= now}) pour l'atelier courant,
     * plus au plus une occurrence anticipée (hebdo/mensuel) dans la fenêtre
     * {@link #EARLY_COMPLETION_DAYS} jours.
     */
    public void generateDueOccurrences() {
        Atelier atelier = atelierService.requireCurrentAtelier();
        generateDueOccurrences(atelier);
    }

    public void generateDueOccurrences(Atelier atelier) {
        generateDueOccurrences(atelier, LocalDateTime.now(clock));
    }

    /**
     * Variante planifiée : {@code now} doit être dans le fuseau {@code SCHED_TIMEZONE}.
     */
    public void generateDueOccurrences(Atelier atelier, LocalDateTime now) {
        LocalDate earlyUntil = now.toLocalDate().plusDays(EARLY_COMPLETION_DAYS);
        List<TodoRecurrence> rules = todoRecurrenceRepository.findActiveByAtelierId(atelier.getId());
        for (TodoRecurrence rule : rules) {
            LocalDateTime next = rule.getProchaineEcheance();
            if (next == null) {
                next = computeFirstDue(rule);
            }
            int guard = 0;
            // Rattrapage : toutes les échéances déjà dues / passées.
            while (next != null && !next.isAfter(now) && guard++ < MAX_CATCH_UP) {
                next = materializeOccurrence(atelier, rule, next);
            }
            // Anticipation : une seule prochaine occurrence WEEKLY/MONTHLY dans les 7 jours.
            if (allowsEarlyCompletion(rule.getFrequence())
                    && next != null
                    && !next.toLocalDate().isAfter(earlyUntil)
                    && guard < MAX_CATCH_UP) {
                next = materializeOccurrence(atelier, rule, next);
            }
            rule.setProchaineEcheance(next);
            todoRecurrenceRepository.save(rule);
        }
    }

    /**
     * Crée l'occurrence si absente, avance le curseur ; {@code null} si hors dateFin.
     */
    private LocalDateTime materializeOccurrence(Atelier atelier, TodoRecurrence rule, LocalDateTime due) {
        if (rule.getDateFin() != null && due.toLocalDate().isAfter(rule.getDateFin())) {
            return null;
        }
        String key = occurrenceKey(due);
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
                    .dueAt(due)
                    .occurrenceKey(key)
                    .build();
            todoTacheRepository.save(occurrence);
            log.info("Occurrence todo générée recurrenceId={} key={} dueAt={}",
                    rule.getId(), key, due);
        }
        return advance(rule, due);
    }

    static boolean allowsEarlyCompletion(TodoRecurrenceFrequence frequence) {
        return frequence == TodoRecurrenceFrequence.WEEKLY
                || frequence == TodoRecurrenceFrequence.MONTHLY;
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

    private void requireAdmin(String username) {
        User actor = requireUser(username);
        if (!Roles.isAdminLike(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La gestion des tâches récurrentes est réservée aux administrateurs");
        }
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
