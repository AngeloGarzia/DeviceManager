package com.devicemanager.controller;

import com.devicemanager.dto.AppSettingResponse;
import com.devicemanager.dto.AppSettingsUpdateRequest;
import com.devicemanager.dto.MailTestResponse;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.MailService;
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
    private final MailService mailService;

    @GetMapping
    public ResponseEntity<List<AppSettingResponse>> list() {
        return ResponseEntity.ok(appSettingsService.list());
    }

    @PutMapping
    public ResponseEntity<List<AppSettingResponse>> update(@Valid @RequestBody AppSettingsUpdateRequest request) {
        return ResponseEntity.ok(appSettingsService.update(request));
    }

    @PostMapping("/mail/test")
    public ResponseEntity<MailTestResponse> testMail() {
        return ResponseEntity.ok(mailService.sendTestEmail());
    }
}
