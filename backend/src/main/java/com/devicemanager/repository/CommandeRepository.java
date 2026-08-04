package com.devicemanager.repository;

import com.devicemanager.entity.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux demandes de commande ({@link Commande}).
 */
public interface CommandeRepository extends JpaRepository<Commande, Long> {

    /**
     * Liste les commandes d'un atelier avec lignes, pièces, SFM, MAS et technicien pré-chargés.
     *
     * @param atelierId identifiant de l'atelier
     * @return commandes triées par date de demande décroissante
     */
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

    /**
     * Compte les commandes d'un atelier dont le statut est dans la liste fournie.
     *
     * @param atelierId identifiant de l'atelier
     * @param statuses  statuts à inclure
     * @return nombre de commandes correspondantes
     */
    @Query("""
            select count(c) from Commande c
            where c.atelier.id = :atelierId
              and c.status in :statuses
            """)
    long countByAtelierIdAndStatusIn(
            @Param("atelierId") Long atelierId,
            @Param("statuses") List<String> statuses);

    /**
     * Charge une commande par identifiant et atelier avec toutes ses relations.
     *
     * @param id        identifiant de la commande
     * @param atelierId identifiant de l'atelier (contrôle multi-tenant)
     * @return commande trouvée ou vide
     */
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

    /**
     * Compte le nombre total de commandes d'un atelier.
     *
     * @param atelierId identifiant de l'atelier
     * @return nombre de commandes
     */
    long countByAtelierId(Long atelierId);
}
