package com.devicemanager.repository;

import com.devicemanager.entity.ArretMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArretMaintenanceRepository extends JpaRepository<ArretMaintenance, Long> {

    @Query("""
            select a from ArretMaintenance a
            join fetch a.mas m
            left join fetch m.marque
            where a.atelier.id = :atelierId and a.dateHeureReprise is null
            order by a.dateHeureArret desc, a.id desc
            """)
    List<ArretMaintenance> findActiveByAtelierId(@Param("atelierId") Long atelierId);

    long countByAtelierIdAndDateHeureRepriseIsNull(Long atelierId);

    @Query("""
            select a from ArretMaintenance a
            join fetch a.mas m
            left join fetch m.marque
            where a.id = :id and a.atelier.id = :atelierId
            """)
    Optional<ArretMaintenance> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    boolean existsByMasIdAndDateHeureRepriseIsNull(Long masId);

    @Query("""
            select a from ArretMaintenance a
            join fetch a.mas m
            left join fetch m.marque
            where a.atelier.id = :atelierId
            order by a.dateHeureArret desc, a.id desc
            """)
    List<ArretMaintenance> findHistoryByAtelierId(@Param("atelierId") Long atelierId);

    /**
     * Arrêts ouverts depuis avant {@code cutoff} (ateliers actifs).
     */
    @Query("""
            select a from ArretMaintenance a
            join fetch a.mas m
            left join fetch m.marque
            join fetch a.atelier at
            where a.dateHeureReprise is null
              and a.dateHeureArret < :cutoff
              and at.utilise = true
            order by a.dateHeureArret asc, a.id asc
            """)
    List<ArretMaintenance> findStaleOpenAcrossAteliers(
            @Param("cutoff") java.time.LocalDateTime cutoff);
}
