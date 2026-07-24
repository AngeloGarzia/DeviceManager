package com.devicemanager.controller;

import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.service.SfmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sfm")
@RequiredArgsConstructor
public class SfmController {

    private final SfmService sfmService;

    @GetMapping
    public ResponseEntity<List<SfmResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(sfmService.findAll(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SfmResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(sfmService.findById(id));
    }

    @PostMapping
    public ResponseEntity<SfmResponse> create(@Valid @RequestBody SfmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sfmService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SfmResponse> update(@PathVariable Long id, @Valid @RequestBody SfmRequest request) {
        return ResponseEntity.ok(sfmService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sfmService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
