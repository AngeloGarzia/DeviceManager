package com.devicemanager.repository;

import com.devicemanager.entity.Casino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux casinos ({@link Casino}).
 */
public interface CasinoRepository extends JpaRepository<Casino, Long> {

    /**
     * Liste les casinos d'un groupe triés par nom.
     *
     * @param groupeId identifiant du groupe
     * @return casinos du groupe
     */
    List<Casino> findByGroupeIdOrderByNomAsc(Long groupeId);

    /**
     * Recherche un casino par nom (insensible à la casse) dans un groupe donné.
     *
     * @param nom      nom du casino
     * @param groupeId identifiant du groupe
     * @return casino trouvé ou vide
     */
    Optional<Casino> findByNomIgnoreCaseAndGroupeId(String nom, Long groupeId);

    /**
     * Charge un casino par identifiant avec son groupe pré-chargé.
     *
     * @param id identifiant du casino
     * @return casino trouvé ou vide
     */
    @Query("""
            SELECT c FROM Casino c
            JOIN FETCH c.groupe
            WHERE c.id = :id
            """)
    Optional<Casino> findByIdWithGroupe(@Param("id") Long id);
}
