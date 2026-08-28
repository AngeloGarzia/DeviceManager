package com.devicemanager.repository;

import com.devicemanager.entity.RegleJeux;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Accès au catalogue global des règles de jeux.
 */
public interface RegleJeuxRepository extends JpaRepository<RegleJeux, Long> {

    List<RegleJeux> findAllByOrderByLabelAsc();

    boolean existsByLabelIgnoreCase(String label);

    boolean existsByLabelIgnoreCaseAndIdNot(String label, Long id);

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = "SELECT COUNT(*) FROM mas_regle_jeux WHERE regle_jeux_id = :regleId", nativeQuery = true)
    long countMasLinks(@Param("regleId") Long regleId);
}
