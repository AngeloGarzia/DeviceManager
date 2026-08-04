package com.devicemanager.repository;

import com.devicemanager.entity.Sfm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux SFM ({@link Sfm}) scopés par atelier.
 */
public interface SfmRepository extends JpaRepository<Sfm, Long> {

    /**
     * Liste tous les SFM d'un atelier avec leurs contacts pré-chargés.
     *
     * @param atelierId identifiant de l'atelier
     * @return SFM triés par nom
     */
    @Query("""
            SELECT DISTINCT s FROM Sfm s
            LEFT JOIN FETCH s.contacts
            WHERE s.atelier.id = :atelierId
            ORDER BY s.nom
            """)
    List<Sfm> findAllWithContacts(@Param("atelierId") Long atelierId);

    /**
     * Recherche textuelle de SFM dans un atelier (nom, responsable, contacts, marques).
     *
     * @param atelierId identifiant de l'atelier
     * @param q         terme de recherche
     * @return SFM correspondants triés par nom
     */
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

    /**
     * Charge un SFM par identifiant et atelier avec ses contacts.
     *
     * @param id        identifiant du SFM
     * @param atelierId identifiant de l'atelier (contrôle multi-tenant)
     * @return SFM trouvé ou vide
     */
    @Query("""
            SELECT s FROM Sfm s
            LEFT JOIN FETCH s.contacts
            WHERE s.id = :id AND s.atelier.id = :atelierId
            """)
    Optional<Sfm> findByIdWithContacts(@Param("id") Long id, @Param("atelierId") Long atelierId);

    /**
     * Vérifie l'existence d'un SFM portant ce nom dans l'atelier (insensible à la casse).
     *
     * @param nom       nom du SFM
     * @param atelierId identifiant de l'atelier
     * @return {@code true} si un doublon existe
     */
    boolean existsByNomIgnoreCaseAndAtelierId(String nom, Long atelierId);

    /**
     * Vérifie l'existence d'un autre SFM portant ce nom dans l'atelier (hors l'identifiant donné).
     *
     * @param nom       nom du SFM
     * @param atelierId identifiant de l'atelier
     * @param id        identifiant du SFM à exclure (mise à jour)
     * @return {@code true} si un doublon existe
     */
    boolean existsByNomIgnoreCaseAndAtelierIdAndIdNot(String nom, Long atelierId, Long id);

    /**
     * Compte le nombre total de SFM d'un atelier.
     *
     * @param atelierId identifiant de l'atelier
     * @return nombre de SFM
     */
    long countByAtelierId(Long atelierId);
}
