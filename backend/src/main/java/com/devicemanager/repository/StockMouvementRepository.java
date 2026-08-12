package com.devicemanager.repository;

import com.devicemanager.entity.StockMouvement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Accès au journal des mouvements de stock.
 */
public interface StockMouvementRepository extends JpaRepository<StockMouvement, Long> {

    @Query("""
            select m from StockMouvement m
            left join fetch m.device
            where m.atelier.id = :atelierId
              and m.sourceType = :sourceType
            order by m.createdAt desc, m.id desc
            """)
    List<StockMouvement> findByAtelierAndSourceType(
            @Param("atelierId") Long atelierId,
            @Param("sourceType") String sourceType);

    @Query("""
            select m from StockMouvement m
            left join fetch m.device
            where m.atelier.id = :atelierId
              and m.sourceType = :sourceType
              and m.createdAt >= :from
              and m.createdAt <= :to
            order by m.createdAt desc, m.id desc
            """)
    List<StockMouvement> findByAtelierAndSourceTypeBetween(
            @Param("atelierId") Long atelierId,
            @Param("sourceType") String sourceType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
