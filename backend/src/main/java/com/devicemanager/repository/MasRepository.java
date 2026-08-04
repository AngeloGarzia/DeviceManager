package com.devicemanager.repository;

import com.devicemanager.entity.Mas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux références MAS ({@link Mas}) scopées par atelier.
 */
public interface MasRepository extends JpaRepository<Mas, Long> {

    /**
     * Recherche textuelle de MAS dans un atelier (numéro, libellé et code marque).
     *
     * @param atelierId identifiant de l'atelier
     * @param q         terme de recherche
     * @return MAS correspondantes triées par numéro
     */
    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            WHERE m.atelier.id = :atelierId
              AND (
                   LOWER(m.numero) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.marque.label) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.marque.code) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            ORDER BY m.numero
            """)
    List<Mas> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    /**
     * Liste toutes les MAS d'un atelier avec marque pré-chargée.
     *
     * @param atelierId identifiant de l'atelier
     * @return MAS triées par numéro
     */
    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            WHERE m.atelier.id = :atelierId
            ORDER BY m.numero
            """)
    List<Mas> findAllByAtelierId(@Param("atelierId") Long atelierId);

    /**
     * Charge une MAS par identifiant et atelier avec sa marque.
     *
     * @param id        identifiant de la MAS
     * @param atelierId identifiant de l'atelier (contrôle multi-tenant)
     * @return MAS trouvée ou vide
     */
    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            WHERE m.id = :id AND m.atelier.id = :atelierId
            """)
    Optional<Mas> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    /**
     * Vérifie l'existence d'une MAS portant ce numéro dans l'atelier (insensible à la casse).
     *
     * @param numero    numéro MAS
     * @param atelierId identifiant de l'atelier
     * @return {@code true} si un doublon existe
     */
    boolean existsByNumeroIgnoreCaseAndAtelierId(String numero, Long atelierId);

    /**
     * Vérifie l'existence d'une autre MAS portant ce numéro dans l'atelier (hors l'identifiant donné).
     *
     * @param numero    numéro MAS
     * @param atelierId identifiant de l'atelier
     * @param id        identifiant de la MAS à exclure (mise à jour)
     * @return {@code true} si un doublon existe
     */
    boolean existsByNumeroIgnoreCaseAndAtelierIdAndIdNot(String numero, Long atelierId, Long id);

    /**
     * Compte le nombre total de MAS d'un atelier.
     *
     * @param atelierId identifiant de l'atelier
     * @return nombre de MAS
     */
    long countByAtelierId(Long atelierId);
}
