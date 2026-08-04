package com.devicemanager.repository;

import com.devicemanager.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux pièces ({@link Device}) scopées par atelier.
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /**
     * Recherche textuelle de pièces dans un atelier (nom, référence, usage, SFM, MAS, marque).
     *
     * @param atelierId identifiant de l'atelier
     * @param q         terme de recherche
     * @return pièces correspondantes triées par nom
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
              AND (
                   LOWER(d.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (d.reference IS NOT NULL AND LOWER(d.reference) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (d.usage IS NOT NULL AND LOWER(d.usage) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (d.sfm IS NOT NULL AND LOWER(d.sfm.nom) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (m IS NOT NULL AND LOWER(m.numero) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (d.marque IS NOT NULL AND LOWER(d.marque.label) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (m.marque IS NOT NULL AND LOWER(m.marque.label) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            ORDER BY d.nom
            """)
    List<Device> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    /**
     * Liste toutes les pièces d'un atelier avec SFM, MAS et marques pré-chargés.
     *
     * @param atelierId identifiant de l'atelier
     * @return pièces triées par nom
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.atelier.id = :atelierId
            ORDER BY d.nom
            """)
    List<Device> findAllWithRelations(@Param("atelierId") Long atelierId);

    /**
     * Charge une pièce par identifiant et atelier avec ses relations.
     *
     * @param id        identifiant de la pièce
     * @param atelierId identifiant de l'atelier (contrôle multi-tenant)
     * @return pièce trouvée ou vide
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.sfm
            LEFT JOIN FETCH d.mas m
            LEFT JOIN FETCH m.marque
            LEFT JOIN FETCH d.marque
            WHERE d.id = :id AND d.atelier.id = :atelierId
            """)
    Optional<Device> findByIdWithRelations(@Param("id") Long id, @Param("atelierId") Long atelierId);

    /**
     * Vérifie l'existence d'une pièce portant ce nom dans l'atelier (insensible à la casse).
     *
     * @param nom       nom de la pièce
     * @param atelierId identifiant de l'atelier
     * @return {@code true} si un doublon existe
     */
    boolean existsByNomIgnoreCaseAndAtelierId(String nom, Long atelierId);

    /**
     * Vérifie l'existence d'une autre pièce portant ce nom dans l'atelier (hors l'identifiant donné).
     *
     * @param nom       nom de la pièce
     * @param atelierId identifiant de l'atelier
     * @param id        identifiant de la pièce à exclure (mise à jour)
     * @return {@code true} si un doublon existe
     */
    boolean existsByNomIgnoreCaseAndAtelierIdAndIdNot(String nom, Long atelierId, Long id);

    /**
     * Vérifie l'existence d'une pièce portant cette référence dans l'atelier (insensible à la casse).
     *
     * @param reference référence de la pièce
     * @param atelierId identifiant de l'atelier
     * @return {@code true} si un doublon existe
     */
    boolean existsByReferenceIgnoreCaseAndAtelierId(String reference, Long atelierId);

    /**
     * Vérifie l'existence d'une autre pièce portant cette référence dans l'atelier (hors l'identifiant donné).
     *
     * @param reference référence de la pièce
     * @param atelierId identifiant de l'atelier
     * @param id        identifiant de la pièce à exclure (mise à jour)
     * @return {@code true} si un doublon existe
     */
    boolean existsByReferenceIgnoreCaseAndAtelierIdAndIdNot(String reference, Long atelierId, Long id);

    /**
     * Compte le nombre total de pièces d'un atelier.
     *
     * @param atelierId identifiant de l'atelier
     * @return nombre de pièces
     */
    long countByAtelierId(Long atelierId);
}
