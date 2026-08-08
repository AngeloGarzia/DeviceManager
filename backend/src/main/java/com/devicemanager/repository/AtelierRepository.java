package com.devicemanager.repository;

import com.devicemanager.entity.Atelier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux ateliers de maintenance ({@link Atelier}).
 */
public interface AtelierRepository extends JpaRepository<Atelier, Long> {

    /**
     * Liste les ateliers d'un groupe avec casino, groupe et coordonnées pré-chargés.
     *
     * @param groupeId identifiant du groupe
     * @return ateliers triés par nom de casino puis nom d'atelier
     */
    @Query("""
            SELECT DISTINCT a FROM Atelier a
            JOIN FETCH a.casino c
            JOIN FETCH c.groupe
            LEFT JOIN FETCH a.coordonnees
            WHERE c.groupe.id = :groupeId
            ORDER BY c.nom, a.nom
            """)
    List<Atelier> findAllByGroupeId(@Param("groupeId") Long groupeId);

    /**
     * Charge un atelier par identifiant avec casino, groupe et coordonnées.
     *
     * @param id identifiant de l'atelier
     * @return atelier trouvé ou vide
     */
    @Query("""
            SELECT a FROM Atelier a
            JOIN FETCH a.casino c
            JOIN FETCH c.groupe
            LEFT JOIN FETCH a.coordonnees
            WHERE a.id = :id
            """)
    Optional<Atelier> findByIdWithCasino(@Param("id") Long id);

    /**
     * Recherche un atelier par nom (insensible à la casse) dans un casino donné.
     *
     * @param nom      nom de l'atelier
     * @param casinoId identifiant du casino
     * @return atelier trouvé ou vide
     */
    Optional<Atelier> findByNomIgnoreCaseAndCasinoId(String nom, Long casinoId);

    /**
     * @param casinoId identifiant du casino
     * @return nombre d'ateliers rattachés
     */
    long countByCasinoId(Long casinoId);

    boolean existsByNomIgnoreCaseAndCasinoId(String nom, Long casinoId);

    boolean existsByNomIgnoreCaseAndCasinoIdAndIdNot(String nom, Long casinoId, Long id);
}
