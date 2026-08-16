package com.devicemanager.repository;

import com.devicemanager.entity.DevicePrixObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DevicePrixObservationRepository extends JpaRepository<DevicePrixObservation, Long> {

    List<DevicePrixObservation> findByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(
            Long deviceId, Long atelierId);

    List<DevicePrixObservation> findTop20ByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(
            Long deviceId, Long atelierId);
}
