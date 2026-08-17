package com.devicemanager.repository;

import com.devicemanager.entity.VisiteQuadritrimestrelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux visites quadritrimestrielles (SFM × marque), scopées par atelier.
 */
public interface VisiteQuadriRepository extends JpaRepository<VisiteQuadritrimestrelle, Long> {

    @Query("""
            SELECT v FROM VisiteQuadritrimestrelle v
            JOIN FETCH v.sfm
            JOIN FETCH v.marque
            WHERE v.atelier.id = :atelierId
              AND (:sfmId IS NULL OR v.sfm.id = :sfmId)
              AND (:marqueId IS NULL OR v.marque.id = :marqueId)
            ORDER BY v.dateVisite DESC, v.id DESC
            """)
    List<VisiteQuadritrimestrelle> findHistory(
            @Param("atelierId") Long atelierId,
            @Param("sfmId") Long sfmId,
            @Param("marqueId") Long marqueId);

    @Query("""
            SELECT MAX(v.dateVisite) FROM VisiteQuadritrimestrelle v
            WHERE v.atelier.id = :atelierId
              AND v.sfm.id = :sfmId
              AND v.marque.id = :marqueId
            """)
    Optional<LocalDate> findLastVisitDate(
            @Param("atelierId") Long atelierId,
            @Param("sfmId") Long sfmId,
            @Param("marqueId") Long marqueId);

    @Query("""
            SELECT v FROM VisiteQuadritrimestrelle v
            WHERE v.atelier.id = :atelierId
              AND v.sfm.id = :sfmId
              AND v.marque.id = :marqueId
            ORDER BY v.dateVisite DESC, v.id DESC
            """)
    List<VisiteQuadritrimestrelle> findByAtelierAndSfmAndMarqueOrderByDateDesc(
            @Param("atelierId") Long atelierId,
            @Param("sfmId") Long sfmId,
            @Param("marqueId") Long marqueId);
}
