package com.devicemanager.controller;

import com.devicemanager.dto.AiChatRequest;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiLabelScanResponse;
import com.devicemanager.service.AiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiChatResponse> status() {
        return ResponseEntity.ok(aiAssistantService.status());
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiAssistantService.chat(request.getMessage()));
    }

    @PostMapping(value = "/label-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiLabelScanResponse> labelScan(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(aiAssistantService.scanLabel(image));
    }
}
