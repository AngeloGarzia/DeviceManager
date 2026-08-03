package com.devicemanager.controller;

import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(deviceService.findAll(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestPart("data") DeviceRequest data,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {
        List<MultipartFile> list = photos == null ? List.of() : Arrays.asList(photos);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.create(data, list));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceResponse> update(
            @PathVariable Long id,
            @Valid @RequestPart("data") DeviceRequest data,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {
        List<MultipartFile> list = photos == null ? List.of() : Arrays.asList(photos);
        return ResponseEntity.ok(deviceService.update(id, data, list));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
