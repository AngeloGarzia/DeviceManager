package com.devicemanager.repository;

import com.devicemanager.entity.UploadBlob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadBlobRepository extends JpaRepository<UploadBlob, String> {
}
