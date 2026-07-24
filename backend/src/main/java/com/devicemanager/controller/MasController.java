package com.devicemanager.controller;

import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.service.MasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mas")
@RequiredArgsConstructor
public class MasController {

    private final MasService masService;

    @GetMapping
    public ResponseEntity<List<MasResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masService.findAll(q));
    }

    @GetMapping("/marques")
    public ResponseEntity<List<MarqueMasResponse>> marques() {
        return ResponseEntity.ok(masService.listMarques());
    }

    @PostMapping("/marques")
    public ResponseEntity<MarqueMasResponse> createMarque(@Valid @RequestBody MarqueMasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masService.createMarque(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MasResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(masService.findById(id));
    }

    @PostMapping
    public ResponseEntity<MasResponse> create(@Valid @RequestBody MasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasResponse> update(@PathVariable Long id, @Valid @RequestBody MasRequest request) {
        return ResponseEntity.ok(masService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        masService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
