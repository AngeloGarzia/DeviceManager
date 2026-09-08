package com.devicemanager.controller;

import com.devicemanager.dto.TodoListResponse;
import com.devicemanager.dto.TodoTacheLinkRequest;
import com.devicemanager.dto.TodoTacheRequest;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.dto.TodoTacheStatusRequest;
import com.devicemanager.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Tâches « À faire » : CRUD, cycle de vie, rattachement d'interventions.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /** Liste des tâches actives (OPEN / IN_PROGRESS). */
    @GetMapping
    public ResponseEntity<TodoListResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return ResponseEntity.ok(all ? todoService.listAll() : todoService.listPending());
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
