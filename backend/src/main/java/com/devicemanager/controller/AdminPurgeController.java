package com.devicemanager.controller;

import com.devicemanager.dto.AdminPurgeExecuteRequest;
import com.devicemanager.dto.AdminPurgePlanResponse;
import com.devicemanager.dto.AdminPurgeResultResponse;
import com.devicemanager.service.AdminPurgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purge en chaîne — Super Admin uniquement ({@code /api/admin/purge/**}).
 */
@RestController
@RequestMapping("/api/admin/purge")
@RequiredArgsConstructor
public class AdminPurgeController {

    private final AdminPurgeService adminPurgeService;

    @GetMapping("/preview")
    public ResponseEntity<AdminPurgePlanResponse> preview(
            @RequestParam String rootType,
            @RequestParam Long rootId) {
        return ResponseEntity.ok(adminPurgeService.preview(rootType, rootId));
    }

    @PostMapping("/execute")
    public ResponseEntity<AdminPurgeResultResponse> execute(
            @Valid @RequestBody AdminPurgeExecuteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(adminPurgeService.execute(request, authentication.getName()));
    }
}
