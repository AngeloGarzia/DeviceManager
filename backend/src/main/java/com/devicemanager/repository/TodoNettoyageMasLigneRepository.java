package com.devicemanager.repository;

import com.devicemanager.entity.TodoNettoyageMasLigne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoNettoyageMasLigneRepository extends JpaRepository<TodoNettoyageMasLigne, Long> {

    Optional<TodoNettoyageMasLigne> findByTodoTacheIdAndMasId(Long todoTacheId, Long masId);

    /**
     * Agrégat par MAS : [masId, max(cocheAt), count].
     */
    @Query("""
            SELECT m.id, MAX(l.cocheAt), COUNT(l)
            FROM TodoNettoyageMasLigne l
            JOIN l.todoTache t
            JOIN l.mas m
            WHERE t.atelier.id = :atelierId
              AND l.coche = true
              AND l.cocheAt IS NOT NULL
            GROUP BY m.id
            """)
    List<Object[]> aggregateDernierParMas(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT DISTINCT l FROM TodoNettoyageMasLigne l
            JOIN FETCH l.todoTache t
            JOIN FETCH l.mas m
            JOIN FETCH t.atelier
            WHERE t.atelier.id = :atelierId
              AND m.id = :masId
              AND l.coche = true
            ORDER BY l.cocheAt DESC, t.completedAt DESC, t.createdAt DESC
            """)
    List<TodoNettoyageMasLigne> findHistoriqueByAtelierAndMas(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId);

    @Query("""
            SELECT DISTINCT l FROM TodoNettoyageMasLigne l
            JOIN FETCH l.todoTache t
            JOIN FETCH l.mas m
            WHERE t.atelier.id = :atelierId
              AND m.id = :masId
              AND l.coche = true
            ORDER BY l.cocheAt DESC, t.completedAt DESC
            """)
    List<TodoNettoyageMasLigne> findCocheesByAtelierAndMas(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId);
}
