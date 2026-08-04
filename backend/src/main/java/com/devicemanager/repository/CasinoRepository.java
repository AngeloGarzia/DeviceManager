package com.devicemanager.repository;

import com.devicemanager.entity.Casino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CasinoRepository extends JpaRepository<Casino, Long> {
    List<Casino> findByGroupeIdOrderByNomAsc(Long groupeId);

    Optional<Casino> findByNomIgnoreCaseAndGroupeId(String nom, Long groupeId);

    @Query("""
            SELECT c FROM Casino c
            JOIN FETCH c.groupe
            WHERE c.id = :id
            """)
    Optional<Casino> findByIdWithGroupe(@Param("id") Long id);
}
