package com.devicemanager.repository;

import com.devicemanager.entity.DevicePrixAlerte;
import com.devicemanager.entity.PrixAlerteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DevicePrixAlerteRepository extends JpaRepository<DevicePrixAlerte, Long> {

    List<DevicePrixAlerte> findByAtelierIdAndStatusOrderByCreatedAtDesc(Long atelierId, PrixAlerteStatus status);

    List<DevicePrixAlerte> findByAtelierIdOrderByCreatedAtDesc(Long atelierId);

    Optional<DevicePrixAlerte> findByIdAndAtelierId(Long id, Long atelierId);

    List<DevicePrixAlerte> findByDeviceIdAndAtelierIdAndStatusOrderByCreatedAtDesc(
            Long deviceId, Long atelierId, PrixAlerteStatus status);
}
