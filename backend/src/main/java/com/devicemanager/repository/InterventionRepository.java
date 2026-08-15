package com.devicemanager.repository;

import com.devicemanager.entity.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux bons d'intervention archivés.
 */
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    @Query("""
            select distinct i from Intervention i
            left join fetch i.lignes l
            left join fetch l.device d
            join fetch i.technicien
            where i.atelier.id = :atelierId
            order by i.dateIntervention desc, i.id desc
            """)
    List<Intervention> findAllWithRelationsByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            select distinct i from Intervention i
            left join fetch i.lignes l
            left join fetch l.device d
            join fetch i.technicien
            join fetch i.atelier
            where i.id = :id and i.atelier.id = :atelierId
            """)
    Optional<Intervention> findByIdWithRelations(@Param("id") Long id, @Param("atelierId") Long atelierId);

    @Query("""
            select distinct i from Intervention i
            left join fetch i.lignes l
            left join fetch l.device d
            join fetch i.technicien
            left join fetch i.mas
            where i.atelier.id = :atelierId
              and (
                   (i.mas is not null and i.mas.id = :masId)
                or (
                      i.mas is null
                  and i.machineMas is not null
                  and (
                       lower(i.machineMas) = lower(:numero)
                    or lower(i.machineMas) like lower(concat(:numero, ' — %'))
                    or lower(i.machineMas) like lower(concat(:numero, ' - %'))
                  )
                )
              )
            order by i.dateIntervention desc, i.id desc
            """)
    List<Intervention> findByAtelierAndMas(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId,
            @Param("numero") String numero);

    long countByAtelierId(Long atelierId);

    @Query("""
            select count(i) from Intervention i
            where i.atelier.id = :atelierId
              and year(i.dateIntervention) = :year
            """)
    long countByAtelierIdAndYear(@Param("atelierId") Long atelierId, @Param("year") int year);
}
