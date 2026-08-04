package com.devicemanager.repository;

import com.devicemanager.entity.Groupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Accès aux groupes organisationnels ({@link Groupe}).
 */
public interface GroupeRepository extends JpaRepository<Groupe, Long> {

    /**
     * Recherche un groupe par nom (insensible à la casse).
     *
     * @param nom nom du groupe
     * @return groupe trouvé ou vide
     */
    Optional<Groupe> findByNomIgnoreCase(String nom);
}
