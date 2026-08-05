package com.devicemanager.repository;

import com.devicemanager.entity.AppLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Persistance des logs applicatifs consultables par les administrateurs.
 */
public interface AppLogRepository extends JpaRepository<AppLog, Long> {

    @Query("""
            SELECT l FROM AppLog l
            WHERE (:levelsEmpty = true OR l.level IN :levels)
              AND (:loggerBlank = true OR LOWER(l.loggerName) LIKE LOWER(CONCAT('%', :logger, '%')))
              AND (:queryBlank = true OR LOWER(l.message) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR (l.throwable IS NOT NULL AND LOWER(l.throwable) LIKE LOWER(CONCAT('%', :query, '%'))))
            ORDER BY l.id DESC
            """)
    List<AppLog> search(
            @Param("levelsEmpty") boolean levelsEmpty,
            @Param("levels") Collection<String> levels,
            @Param("loggerBlank") boolean loggerBlank,
            @Param("logger") String logger,
            @Param("queryBlank") boolean queryBlank,
            @Param("query") String query,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM app_log
            WHERE id NOT IN (
              SELECT id FROM (
                SELECT id FROM app_log ORDER BY id DESC LIMIT :keep
              ) kept
            )
            """, nativeQuery = true)
    int pruneKeepingNewest(@Param("keep") int keep);
}
