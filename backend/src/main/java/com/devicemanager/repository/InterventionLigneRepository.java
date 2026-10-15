package com.devicemanager.repository;

import com.devicemanager.entity.InterventionLigne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterventionLigneRepository extends JpaRepository<InterventionLigne, Long> {

    @Query("""
            select distinct l from InterventionLigne l
            join fetch l.intervention i
            where l.device.id = :deviceId
            """)
    List<InterventionLigne> findByDeviceIdWithIntervention(@Param("deviceId") Long deviceId);

    long countByDeviceId(Long deviceId);
}
