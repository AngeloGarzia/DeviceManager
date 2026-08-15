package com.devicemanager.repository;

import com.devicemanager.entity.FitLigne;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux lignes d'historique FIT.
 */
public interface FitLigneRepository extends JpaRepository<FitLigne, Long> {
}
