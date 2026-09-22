package com.devicemanager.controller;

import com.devicemanager.dto.TodoListResponse;
import com.devicemanager.dto.TodoModeleRequest;
import com.devicemanager.dto.TodoModeleResponse;
import com.devicemanager.dto.TodoRecurrenceRequest;
import com.devicemanager.dto.TodoRecurrenceResponse;
import com.devicemanager.dto.TodoRecurrenceToggleRequest;
import com.devicemanager.dto.TodoTacheLinkRequest;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.dto.TodoTacheStatusRequest;
import com.devicemanager.dto.TodoWeekCalendarResponse;
import com.devicemanager.service.TodoModeleService;
import com.devicemanager.service.TodoRecurrenceService;
import com.devicemanager.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tâches « À faire » : CRUD, cycle de vie, rattachement d'interventions, récurrences.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final TodoRecurrenceService todoRecurrenceService;
    private final TodoModeleService todoModeleService;

    /** Liste des tâches actives (OPEN / IN_PROGRESS). */
    @GetMapping
    public ResponseEntity<TodoListResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return ResponseEntity.ok(all ? todoService.listAll() : todoService.listPending());
    }

    @GetMapping("/week-calendar")
    public ResponseEntity<TodoWeekCalendarResponse> weekCalendar() {
        return ResponseEntity.ok(todoRecurrenceService.weekCalendar());
    }

    @GetMapping("/recurrences")
    public ResponseEntity<List<TodoRecurrenceResponse>> listRecurrences() {
        return ResponseEntity.ok(todoRecurrenceService.list());
    }

    @GetMapping("/modeles")
    public ResponseEntity<List<TodoModeleResponse>> listModeles() {
        return ResponseEntity.ok(todoModeleService.list());
    }

    @PostMapping("/modeles")
    public ResponseEntity<TodoModeleResponse> createModele(
            @Valid @RequestBody TodoModeleRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoModeleService.create(request, authentication.getName()));
    }

    @PutMapping("/modeles/{id}")
    public ResponseEntity<TodoModeleResponse> updateModele(
            @PathVariable Long id,
            @Valid @RequestBody TodoModeleRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(todoModeleService.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<Void> deleteModele(@PathVariable Long id, Authentication authentication) {
        todoModeleService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Crée une tâche ponctuelle à partir d'un modèle. */
    @PostMapping("/modeles/{id}/utiliser")
    public ResponseEntity<TodoTacheResponse> utiliserModele(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoModeleService.utiliser(id, authentication.getName()));
    }

    @PostMapping("/recurrences")
    public ResponseEntity<TodoRecurrenceResponse> createRecurrence(
            @Valid @RequestBody TodoRecurrenceRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoRecurrenceService.create(request, authentication.getName()));
    }

    @PutMapping("/recurrences/{id}")
    public ResponseEntity<TodoRecurrenceResponse> updateRecurrence(
            @PathVariable Long id,
            @Valid @RequestBody TodoRecurrenceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(todoRecurrenceService.update(id, request, authentication.getName()));
    }

    @PutMapping("/recurrences/{id}/active")
    public ResponseEntity<TodoRecurrenceResponse> setRecurrenceActive(
            @PathVariable Long id,
            @Valid @RequestBody TodoRecurrenceToggleRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(
                todoRecurrenceService.setActive(id, Boolean.TRUE.equals(body.getActive()), authentication.getName()));
    }

    @DeleteMapping("/recurrences/{id}")
    public ResponseEntity<Void> deleteRecurrence(@PathVariable Long id, Authentication authentication) {
        todoRecurrenceService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<TodoTacheResponse> create(
            @Valid @RequestBody TodoTacheRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoService.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoTacheResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TodoTacheRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(todoService.update(id, request, authentication.getName()));
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<TodoTacheResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody TodoTacheStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(todoService.changeStatus(id, request, authentication.getName()));
    }

    @PutMapping("/{id}/intervention")
    public ResponseEntity<TodoTacheResponse> linkIntervention(
            @PathVariable Long id,
            @RequestBody TodoTacheLinkRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(todoService.linkIntervention(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        todoService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
