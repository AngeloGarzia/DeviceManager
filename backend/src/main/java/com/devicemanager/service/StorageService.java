package com.devicemanager.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StoredObject store(MultipartFile file);

    void delete(String key);

    record StoredObject(String key, String url, String contentType, long size) {
    }
}
