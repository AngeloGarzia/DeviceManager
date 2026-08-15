package com.devicemanager.repository;

import com.devicemanager.entity.Fit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux fiches FIT (inventaire / intervention technique).
 */
public interface FitRepository extends JpaRepository<Fit, Long> {

    @Query("""
            select distinct f from Fit f
            left join fetch f.lignes
            left join fetch f.mas
            where f.atelier.id = :atelierId
            order by f.numeroMachineCasino
            """)
    List<Fit> findAllByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            select distinct f from Fit f
            left join fetch f.lignes
            left join fetch f.mas m
            left join fetch m.marque
            where f.id = :id and f.atelier.id = :atelierId
            """)
    Optional<Fit> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    Optional<Fit> findByAtelierIdAndNumeroMachineCasinoIgnoreCase(Long atelierId, String numeroMachineCasino);

    Optional<Fit> findByAtelierIdAndMasId(Long atelierId, Long masId);

    boolean existsByAtelierIdAndNumeroMachineCasinoIgnoreCase(Long atelierId, String numeroMachineCasino);

    long countByAtelierId(Long atelierId);

    @Query("""
            select distinct f from Fit f
            left join fetch f.lignes
            left join fetch f.mas
            where f.atelier.id = :atelierId and f.mas.id = :masId
            """)
    Optional<Fit> findByAtelierIdAndMasIdWithLignes(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId);
}
