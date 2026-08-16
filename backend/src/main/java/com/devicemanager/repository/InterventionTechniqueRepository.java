package com.devicemanager.repository;

import com.devicemanager.entity.InterventionTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux interventions techniques libres (table {@code interventions}).
 */
public interface InterventionTechniqueRepository extends JpaRepository<InterventionTechnique, Long> {

    @Query("""
            select distinct i from InterventionTechnique i
            join fetch i.mas m
            left join fetch m.marque
            join fetch i.technicien
            left join fetch i.fit
            left join fetch i.commande
            left join fetch i.bonIntervention
            where i.atelier.id = :atelierId
            order by i.dateIntervention desc, i.id desc
            """)
    List<InterventionTechnique> findAllByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            select distinct i from InterventionTechnique i
            join fetch i.mas m
            left join fetch m.marque
            join fetch i.technicien
            left join fetch i.fit
            left join fetch i.commande
            left join fetch i.bonIntervention
            where i.atelier.id = :atelierId and i.mas.id = :masId
            order by i.dateIntervention desc, i.id desc
            """)
    List<InterventionTechnique> findByAtelierIdAndMasId(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId);

    @Query("""
            select distinct i from InterventionTechnique i
            join fetch i.mas m
            left join fetch m.marque
            join fetch i.technicien
            left join fetch i.fit
            left join fetch i.fitLigne
            left join fetch i.commande
            left join fetch i.bonIntervention
            where i.id = :id and i.atelier.id = :atelierId
            """)
    Optional<InterventionTechnique> findByIdAndAtelierId(
            @Param("id") Long id,
            @Param("atelierId") Long atelierId);

    @Query("""
            select distinct i from InterventionTechnique i
            join fetch i.mas m
            left join fetch m.marque
            where i.visiteGroupeId = :visiteGroupeId and i.atelier.id = :atelierId
            order by m.numero
            """)
    List<InterventionTechnique> findByVisiteGroupeIdAndAtelierId(
            @Param("visiteGroupeId") String visiteGroupeId,
            @Param("atelierId") Long atelierId);

    long countByAtelierId(Long atelierId);

    @Query("""
            select distinct i.mas.id from InterventionTechnique i
            where i.atelier.id = :atelierId and i.mas is not null
            """)
    List<Long> findDistinctMasIdsByAtelierId(@Param("atelierId") Long atelierId);
}
