package com.devicemanager.repository;

import com.devicemanager.entity.Sfm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SfmRepository extends JpaRepository<Sfm, Long> {

    @Query("""
            SELECT DISTINCT s FROM Sfm s
            LEFT JOIN FETCH s.contacts
            WHERE s.atelier.id = :atelierId
            ORDER BY s.nom
            """)
    List<Sfm> findAllWithContacts(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT DISTINCT s FROM Sfm s
            LEFT JOIN FETCH s.contacts c
            WHERE s.atelier.id = :atelierId
              AND (
                   LOWER(s.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(s.responsable) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%'))
                OR s.telephone LIKE CONCAT('%', :q, '%')
                OR LOWER(c.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))
                OR c.telephone LIKE CONCAT('%', :q, '%')
                OR EXISTS (
                    SELECT 1 FROM s.marques mq
                    WHERE LOWER(mq.label) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(mq.code) LIKE LOWER(CONCAT('%', :q, '%'))
                )
              )
            ORDER BY s.nom
            """)
    List<Sfm> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    @Query("""
            SELECT s FROM Sfm s
            LEFT JOIN FETCH s.contacts
            WHERE s.id = :id AND s.atelier.id = :atelierId
            """)
    Optional<Sfm> findByIdWithContacts(@Param("id") Long id, @Param("atelierId") Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierId(String nom, Long atelierId);

    boolean existsByNomIgnoreCaseAndAtelierIdAndIdNot(String nom, Long atelierId, Long id);

    long countByAtelierId(Long atelierId);
}
