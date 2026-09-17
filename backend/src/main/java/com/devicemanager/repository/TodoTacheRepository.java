package com.devicemanager.repository;

import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TodoTacheRepository extends JpaRepository<TodoTache, Long> {

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            LEFT JOIN FETCH t.recurrence
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
            LEFT JOIN FETCH t.recurrence
            WHERE t.id = :id AND t.atelier.id = :atelierId
            """)
    Optional<TodoTache> findByIdAndAtelierId(@Param("id") Long id, @Param("atelierId") Long atelierId);

    long countByAtelierIdAndStatutIn(Long atelierId, Collection<TodoTacheStatut> statuts);

    @Query("""
            SELECT t FROM TodoTache t
            WHERE t.atelier.id = :atelierId
              AND t.statut IN :statuts
              AND t.description LIKE CONCAT('%', :marker, '%')
            ORDER BY t.createdAt DESC
            """)
    List<TodoTache> findByAtelierIdAndStatutInAndDescriptionContaining(
            @Param("atelierId") Long atelierId,
            @Param("statuts") Collection<TodoTacheStatut> statuts,
            @Param("marker") String marker);

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            WHERE t.atelier.id = :atelierId
              AND t.mas.id = :masId
            ORDER BY t.createdAt DESC
            """)
    List<TodoTache> findByAtelierIdAndMasId(
            @Param("atelierId") Long atelierId,
            @Param("masId") Long masId);

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            WHERE t.atelier.id = :atelierId
              AND t.mas IS NOT NULL
            ORDER BY t.createdAt DESC
            """)
    List<TodoTache> findAllWithMasByAtelierId(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT t.id FROM TodoTache t
            WHERE t.atelier.id = :atelierId
              AND t.mas IS NOT NULL
            ORDER BY t.createdAt DESC
            """)
    List<Long> findIdsWithMasByAtelierIdOrderByCreatedDesc(
            @Param("atelierId") Long atelierId, Pageable pageable);

    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            LEFT JOIN FETCH t.mas
            LEFT JOIN FETCH t.interventionTechnique
            LEFT JOIN FETCH t.intervention
            WHERE t.id IN :ids
            """)
    List<TodoTache> findWithMasByIds(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT DISTINCT t.mas.id FROM TodoTache t
            WHERE t.atelier.id = :atelierId AND t.mas IS NOT NULL
            """)
    List<Long> findDistinctMasIdsByAtelierId(@Param("atelierId") Long atelierId);

    boolean existsByRecurrenceIdAndOccurrenceKey(Long recurrenceId, String occurrenceKey);

    @Query("""
            SELECT t FROM TodoTache t
            JOIN FETCH t.recurrence
            WHERE t.atelier.id = :atelierId
              AND t.statut IN :statuts
              AND t.recurrence IS NOT NULL
              AND t.dueAt IS NOT NULL
              AND t.dueAt >= :fromInclusive
              AND t.dueAt < :toExclusive
            """)
    List<TodoTache> findRecurringActiveDueBetween(
            @Param("atelierId") Long atelierId,
            @Param("statuts") Collection<TodoTacheStatut> statuts,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Tâches actives en retard (dueAt &lt; now), non liées à une IT, ateliers actifs.
     */
    @Query("""
            SELECT DISTINCT t FROM TodoTache t
            JOIN FETCH t.atelier a
            JOIN FETCH a.casino cas
            JOIN FETCH cas.groupe
            LEFT JOIN FETCH t.mas
            WHERE t.statut IN :statuts
              AND t.dueAt IS NOT NULL
              AND t.dueAt < :now
              AND t.interventionTechnique IS NULL
              AND a.utilise = true
            ORDER BY t.dueAt ASC
            """)
    List<TodoTache> findOverdueAcrossAteliers(
            @Param("statuts") Collection<TodoTacheStatut> statuts,
            @Param("now") LocalDateTime now);
}
