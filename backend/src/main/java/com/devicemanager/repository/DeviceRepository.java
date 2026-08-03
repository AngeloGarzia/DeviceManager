package com.devicemanager.repository;

import com.devicemanager.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
              AND (
                   LOWER(d.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (d.reference IS NOT NULL AND LOWER(d.reference) LIKE LOWER(CONCAT('%', :q, '%')))
                OR LOWER(d.usage) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (d.sfm IS NOT NULL AND LOWER(d.sfm.nom) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (m IS NOT NULL AND LOWER(m.numero) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (d.marque IS NOT NULL AND LOWER(d.marque.label) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (m.marque IS NOT NULL AND LOWER(m.marque.label) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            ORDER BY d.nom
            """)
    List<Device> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
            ORDER BY d.nom
            """)
    List<Device> findAllWithRelations(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.id = :id AND d.atelier.id = :atelierId
            """)
    Optional<Device> findByIdWithRelations(@Param("id") Long id, @Param("atelierId") Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierId(String nom, Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierIdAndIdNot(String nom, Long atelierId, Long id);

    boolean existsByReferenceIgnoreCaseAndAtelierId(String reference, Long atelierId);

    boolean existsByReferenceIgnoreCaseAndAtelierIdAndIdNot(String reference, Long atelierId, Long id);
}
