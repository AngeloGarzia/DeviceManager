package com.devicemanager.controller;

import com.devicemanager.dto.TodoListResponse;
import com.devicemanager.dto.TodoRecurrenceRequest;
import com.devicemanager.dto.TodoRecurrenceResponse;
import com.devicemanager.dto.TodoTacheLinkRequest;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.dto.TodoTacheStatusRequest;
import com.devicemanager.service.TodoRecurrenceService;
import com.devicemanager.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tâches « À faire » : CRUD, cycle de vie, rattachement d'interventions, récurrences.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final TodoRecurrenceService todoRecurrenceService;

    /** Liste des tâches actives (OPEN / IN_PROGRESS). */
    @GetMapping
    public ResponseEntity<TodoListResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return ResponseEntity.ok(all ? todoService.listAll() : todoService.listPending());
    }

    @GetMapping("/recurrences")
    public ResponseEntity<List<TodoRecurrenceResponse>> listRecurrences() {
        return ResponseEntity.ok(todoRecurrenceService.list());
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
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(todoRecurrenceService.setActive(id, active, authentication.getName()));
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
