package com.devicemanager.repository;

import com.devicemanager.entity.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
