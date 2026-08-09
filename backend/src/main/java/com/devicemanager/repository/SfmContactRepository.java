package com.devicemanager.repository;

import com.devicemanager.entity.SfmContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux contacts SFM partageables.
 */
public interface SfmContactRepository extends JpaRepository<SfmContact, Long> {

    Optional<SfmContact> findByEmailIgnoreCase(String email);

    /**
     * Techniciens déjà rattachés à au moins un SFM de l'atelier (réutilisation multi-SFM).
     */
    @Query("""
            SELECT DISTINCT c FROM SfmContact c
            JOIN c.sfms s
            WHERE c.technicienSfm = true
              AND s.atelier.id = :atelierId
            ORDER BY c.nom ASC, c.email ASC
            """)
    List<SfmContact> findTechniciensByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT COUNT(s) FROM Sfm s JOIN s.contacts c WHERE c.id = :contactId
            """)
    long countSfmsByContactId(@Param("contactId") Long contactId);
}
