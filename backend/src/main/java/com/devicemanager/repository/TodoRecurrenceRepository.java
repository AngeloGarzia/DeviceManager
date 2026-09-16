package com.devicemanager.repository;

import com.devicemanager.entity.TodoRecurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoRecurrenceRepository extends JpaRepository<TodoRecurrence, Long> {

    @Query("""
            SELECT DISTINCT r FROM TodoRecurrence r
            LEFT JOIN FETCH r.mas
            WHERE r.atelier.id = :atelierId
            ORDER BY r.active DESC, r.titre ASC
            """)
    List<TodoRecurrence> findAllByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT DISTINCT r FROM TodoRecurrence r
            LEFT JOIN FETCH r.mas
            WHERE r.atelier.id = :atelierId AND r.active = true
            ORDER BY r.id ASC
            """)
    List<TodoRecurrence> findActiveByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT DISTINCT r FROM TodoRecurrence r
            LEFT JOIN FETCH r.mas
            WHERE r.id = :id AND r.atelier.id = :atelierId
            """)
    Optional<TodoRecurrence> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);
}
