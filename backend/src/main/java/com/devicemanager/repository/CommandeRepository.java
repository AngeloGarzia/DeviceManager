package com.devicemanager.repository;

import com.devicemanager.entity.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    @Query("""
            select distinct c from Commande c
            left join fetch c.lignes l
            left join fetch l.device d
            left join fetch d.sfm
            left join fetch d.mas
            join fetch c.technicien
            where c.atelier.id = :atelierId
            order by c.dateDemande desc
            """)
    List<Commande> findAllWithRelationsOrderByDateDesc(@Param("atelierId") Long atelierId);

    @Query("""
            select count(c) from Commande c
            where c.atelier.id = :atelierId
              and c.status in :statuses
            """)
    long countByAtelierIdAndStatusIn(
            @Param("atelierId") Long atelierId,
            @Param("statuses") List<String> statuses);

    @Query("""
            select distinct c from Commande c
            left join fetch c.lignes l
            left join fetch l.device d
            left join fetch d.sfm
            left join fetch d.mas
            join fetch c.technicien
            join fetch c.atelier
            where c.id = :id and c.atelier.id = :atelierId
            """)
    Optional<Commande> findByIdWithRelations(@Param("id") Long id, @Param("atelierId") Long atelierId);
}
