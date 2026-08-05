package com.devicemanager.service;

import com.devicemanager.dto.AdminLogEntryResponse;
import com.devicemanager.dto.AdminLogListResponse;
import com.devicemanager.entity.AppLog;
import com.devicemanager.logging.AppLogDbWriter;
import com.devicemanager.repository.AppLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Consultation et purge des logs SLF4J persistés en base (admins uniquement).
 */
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminLogService.class);

    private final AppLogRepository appLogRepository;
    private final AppLogDbWriter appLogDbWriter;

    @Value("${app.logs.retention-max:5000}")
    private int retentionMax;

    /**
     * Liste les événements persistés, filtrés et limités.
     *
     * @param level  niveau minimum (optionnel)
     * @param logger filtre logger (optionnel)
     * @param query  recherche texte (optionnel)
     * @param limit  plafond (défaut 200)
     * @return snapshot pour l'API admin
     */
    @Transactional(readOnly = true)
    public AdminLogListResponse list(String level, String logger, String query, Integer limit) {
        appLogDbWriter.flush();
        int capped = limit == null ? 200 : Math.min(Math.max(limit, 1), 1000);
        String loggerFilter = blankToNull(logger);
        String queryFilter = blankToNull(query);
        List<String> levels = levelsAtLeast(level);
        boolean levelsEmpty = levels.isEmpty();

        List<AppLog> rows = appLogRepository.search(
                levelsEmpty,
                levelsEmpty ? List.of("_") : levels,
                loggerFilter == null,
                loggerFilter == null ? "" : loggerFilter,
                queryFilter == null,
                queryFilter == null ? "" : queryFilter,
                PageRequest.of(0, capped)
        );

        List<AdminLogEntryResponse> items = rows.stream()
                .map(e -> new AdminLogEntryResponse(
                        e.getId(),
                        e.getCreatedAt(),
                        e.getLevel(),
                        e.getLoggerName(),
                        e.getThreadName(),
                        e.getMessage(),
                        e.getThrowable()))
                .toList();
        long total = appLogRepository.count();
        return new AdminLogListResponse((int) Math.min(total, Integer.MAX_VALUE), retentionMax, items.size(), items);
    }

    /** Supprime tous les logs en base. */
    @Transactional
    public void clear() {
        appLogDbWriter.flush();
        appLogRepository.deleteAllInBatch();
        log.info("Journal applicatif (app_log) vidé par un administrateur");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static List<String> levelsAtLeast(String minLevel) {
        if (minLevel == null || minLevel.isBlank()) {
            return List.of();
        }
        String key = minLevel.trim().toUpperCase(Locale.ROOT);
        if ("WARNING".equals(key)) {
            key = "WARN";
        }
        List<String> all = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        int idx = all.indexOf(key);
        if (idx < 0) {
            return List.of();
        }
        return new ArrayList<>(all.subList(idx, all.size()));
    }
}
