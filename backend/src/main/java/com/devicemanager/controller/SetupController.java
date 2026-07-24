package com.devicemanager.controller;

import com.devicemanager.dto.AppSettingResponse;
import com.devicemanager.dto.AppSettingsUpdateRequest;
import com.devicemanager.service.AppSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final AppSettingsService appSettingsService;

    @GetMapping
    public ResponseEntity<List<AppSettingResponse>> list() {
        return ResponseEntity.ok(appSettingsService.list());
    }

    @PutMapping
    public ResponseEntity<List<AppSettingResponse>> update(@Valid @RequestBody AppSettingsUpdateRequest request) {
        return ResponseEntity.ok(appSettingsService.update(request));
    }
}
