package com.devicemanager.repository;

import com.devicemanager.entity.Atelier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtelierRepository extends JpaRepository<Atelier, Long> {

    @Query("""
            SELECT DISTINCT a FROM Atelier a
            JOIN FETCH a.casino c
            JOIN FETCH c.groupe
            LEFT JOIN FETCH a.coordonnees
            WHERE c.groupe.id = :groupeId
            ORDER BY c.nom, a.nom
            """)
    List<Atelier> findAllByGroupeId(@Param("groupeId") Long groupeId);

    @Query("""
            SELECT a FROM Atelier a
            JOIN FETCH a.casino c
            JOIN FETCH c.groupe
            LEFT JOIN FETCH a.coordonnees
            WHERE a.id = :id
            """)
    Optional<Atelier> findByIdWithCasino(@Param("id") Long id);

    Optional<Atelier> findByNomIgnoreCaseAndCasinoId(String nom, Long casinoId);
}
