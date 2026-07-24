package com.devicemanager.repository;

import com.devicemanager.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
            SELECT d FROM Device d
            JOIN FETCH d.sfm
            JOIN FETCH d.mas m
            JOIN FETCH m.marque
            JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
              AND (
                   LOWER(d.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(d.reference) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(d.usage) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(d.sfm.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.numero) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(d.marque.label) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.marque.label) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            ORDER BY d.nom
            """)
    List<Device> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    @Query("""
            SELECT d FROM Device d
            JOIN FETCH d.sfm
            JOIN FETCH d.mas m
            JOIN FETCH m.marque
            JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
            ORDER BY d.nom
            """)
    List<Device> findAllWithRelations(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT d FROM Device d
            JOIN FETCH d.sfm
            JOIN FETCH d.mas m
            JOIN FETCH m.marque
            JOIN FETCH d.marque
            WHERE d.id = :id AND d.atelier.id = :atelierId
            """)
    Optional<Device> findByIdWithRelations(@Param("id") Long id, @Param("atelierId") Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierId(String nom, Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierIdAndIdNot(String nom, Long atelierId, Long id);

    boolean existsByReferenceIgnoreCaseAndAtelierId(String reference, Long atelierId);

    boolean existsByReferenceIgnoreCaseAndAtelierIdAndIdNot(String reference, Long atelierId, Long id);
}
