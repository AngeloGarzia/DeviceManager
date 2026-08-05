package com.devicemanager.logging;

import com.devicemanager.entity.AppLog;
import com.devicemanager.repository.AppLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Drain asynchrone de la file d'événements SLF4J vers la table {@code app_log}.
 */
@Component
@RequiredArgsConstructor
public class AppLogDbWriter {

    private static final Logger log = LoggerFactory.getLogger(AppLogDbWriter.class);

    private final AppLogRepository appLogRepository;
    private final ConcurrentLinkedQueue<AdminDbLogAppender.StoredLogEvent> queue = new ConcurrentLinkedQueue<>();

    @Value("${app.logs.retention-max:5000}")
    private int retentionMax;

    @Value("${app.logs.flush-batch-size:100}")
    private int flushBatchSize;

    @PostConstruct
    void registerSink() {
        AdminDbLogAppender.setSink(queue::offer);
        log.info("Persistance des logs admin activée (rétention={})", retentionMax);
    }

    @PreDestroy
    void unregisterSink() {
        AdminDbLogAppender.setSink(null);
        flush();
    }

    /** Vide périodiquement la file vers MySQL et applique la rétention. */
    @Scheduled(fixedDelayString = "${app.logs.flush-interval-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flush() {
        List<AppLog> batch = new ArrayList<>(flushBatchSize);
        AdminDbLogAppender.StoredLogEvent event;
        while (batch.size() < flushBatchSize && (event = queue.poll()) != null) {
            batch.add(toEntity(event));
        }
        if (batch.isEmpty()) {
            return;
        }
        appLogRepository.saveAll(batch);
        long total = appLogRepository.count();
        if (total > retentionMax) {
            appLogRepository.pruneKeepingNewest(retentionMax);
        }
    }

    private AppLog toEntity(AdminDbLogAppender.StoredLogEvent event) {
        String message = event.message() == null ? "" : event.message();
        if (message.length() > 8000) {
            message = message.substring(0, 8000) + "…";
        }
        String throwable = event.throwable();
        if (throwable != null && throwable.length() > 32000) {
            throwable = throwable.substring(0, 32000) + "…";
        }
        String logger = event.logger() == null ? "unknown" : event.logger();
        if (logger.length() > 255) {
            logger = logger.substring(0, 255);
        }
        String thread = event.thread();
        if (thread != null && thread.length() > 120) {
            thread = thread.substring(0, 120);
        }
        return AppLog.builder()
                .createdAt(event.timestamp())
                .level(event.level() == null ? "INFO" : event.level())
                .loggerName(logger)
                .threadName(thread)
                .message(message)
                .throwable(throwable)
                .build();
    }
}
