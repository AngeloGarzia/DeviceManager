package com.devicemanager.repository;

import com.devicemanager.entity.Mas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux références MAS ({@link Mas}) scopées par atelier.
 */
public interface MasRepository extends JpaRepository<Mas, Long> {

    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            LEFT JOIN FETCH m.deno
            WHERE m.atelier.id = :atelierId
              AND (
                   LOWER(m.numero) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (m.numeroSocle IS NOT NULL AND LOWER(m.numeroSocle) LIKE LOWER(CONCAT('%', :q, '%')))
                OR LOWER(m.marque.label) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.marque.code) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (m.deno IS NOT NULL AND LOWER(m.deno.label) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (m.multiDeno = TRUE AND LOWER('MultiDéno') LIKE LOWER(CONCAT('%', :q, '%')))
              )
            ORDER BY m.numero
            """)
    List<Mas> search(@Param("atelierId") Long atelierId, @Param("q") String q);

    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            LEFT JOIN FETCH m.deno
            WHERE m.atelier.id = :atelierId
            ORDER BY m.numero
            """)
    List<Mas> findAllByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT m FROM Mas m
            JOIN FETCH m.marque
            LEFT JOIN FETCH m.deno
            WHERE m.id = :id AND m.atelier.id = :atelierId
            """)
    Optional<Mas> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    boolean existsByNumeroIgnoreCaseAndAtelierId(String numero, Long atelierId);

    boolean existsByNumeroIgnoreCaseAndAtelierIdAndIdNot(String numero, Long atelierId, Long id);

    long countByAtelierId(Long atelierId);
}
