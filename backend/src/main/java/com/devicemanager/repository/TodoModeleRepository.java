package com.devicemanager.repository;

import com.devicemanager.entity.TodoModele;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoModeleRepository extends JpaRepository<TodoModele, Long> {

    @Query("""
            SELECT m FROM TodoModele m
            LEFT JOIN FETCH m.mas
            WHERE m.atelier.id = :atelierId
            ORDER BY m.position ASC, m.titre ASC
            """)
    List<TodoModele> findAllByAtelierIdOrderByPosition(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT m FROM TodoModele m
            LEFT JOIN FETCH m.mas
            WHERE m.id = :id AND m.atelier.id = :atelierId
            """)
    Optional<TodoModele> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);
}
