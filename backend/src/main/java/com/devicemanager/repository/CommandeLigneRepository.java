package com.devicemanager.repository;

import com.devicemanager.entity.CommandeLigne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommandeLigneRepository extends JpaRepository<CommandeLigne, Long> {

    @Query("""
            select distinct l from CommandeLigne l
            join fetch l.commande c
            where l.device.id = :deviceId
            """)
    List<CommandeLigne> findByDeviceIdWithCommande(@Param("deviceId") Long deviceId);

    long countByDeviceId(Long deviceId);
}
