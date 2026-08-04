package com.devicemanager.repository;

import com.devicemanager.entity.UploadBlob;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux blobs binaires des uploads locaux ({@link UploadBlob}).
 */
public interface UploadBlobRepository extends JpaRepository<UploadBlob, String> {
}
