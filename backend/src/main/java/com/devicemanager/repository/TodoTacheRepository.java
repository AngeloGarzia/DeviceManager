package com.devicemanager.repository;

import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TodoTacheRepository extends JpaRepository<TodoTache, Long> {

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            WHERE t.atelier.id = :atelierId
              AND t.statut IN :statuts
            ORDER BY t.createdAt DESC
            """)
    List<TodoTache> findByAtelierIdAndStatutIn(
            @Param("atelierId") Long atelierId,
            @Param("statuts") Collection<TodoTacheStatut> statuts);

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            WHERE t.id = :id AND t.atelier.id = :atelierId
            """)
    Optional<TodoTache> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    long countByAtelierIdAndStatutIn(Long atelierId, Collection<TodoTacheStatut> statuts);
}
