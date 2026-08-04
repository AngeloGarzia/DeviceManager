package com.devicemanager.repository;

import com.devicemanager.entity.MarqueMas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Accès au catalogue des marques MAS ({@link MarqueMas}).
 */
public interface MarqueMasRepository extends JpaRepository<MarqueMas, Long> {

    /**
     * Retourne toutes les marques triées par libellé.
     *
     * @return marques ordonnées
     */
    List<MarqueMas> findAllByOrderByLabelAsc();

    /**
     * Recherche une marque par code (insensible à la casse).
     *
     * @param code code court de la marque
     * @return marque trouvée ou vide
     */
    Optional<MarqueMas> findByCodeIgnoreCase(String code);

    /**
     * Recherche une marque par libellé (insensible à la casse).
     *
     * @param label libellé de la marque
     * @return marque trouvée ou vide
     */
    Optional<MarqueMas> findByLabelIgnoreCase(String label);

    /**
     * Vérifie l'existence d'une marque portant ce code.
     *
     * @param code code à vérifier
     * @return {@code true} si le code existe déjà
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Vérifie l'existence d'une marque portant ce libellé.
     *
     * @param label libellé à vérifier
     * @return {@code true} si le libellé existe déjà
     */
    boolean existsByLabelIgnoreCase(String label);
}
