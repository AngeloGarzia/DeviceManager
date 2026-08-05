package com.devicemanager.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Appender Logback (SLF4J) qui envoie les événements vers la persistance admin (MySQL).
 * <p>
 * Le sink Spring est enregistré au démarrage par {@code AppLogDbWriter}.
 * Les logs du writer lui-même sont ignorés pour éviter toute récursion.
 */
public class AdminDbLogAppender extends AppenderBase<ILoggingEvent> {

    private static volatile Consumer<StoredLogEvent> sink;

    /**
     * Enregistre le consommateur Spring (persistance asynchrone).
     *
     * @param consumer sink, ou {@code null} pour désactiver
     */
    public static void setSink(Consumer<StoredLogEvent> consumer) {
        sink = consumer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        String loggerName = event.getLoggerName();
        if (loggerName != null && loggerName.startsWith("com.devicemanager.logging.AppLogDbWriter")) {
            return;
        }
        Consumer<StoredLogEvent> current = sink;
        if (current == null) {
            return;
        }
        String throwable = null;
        if (event.getThrowableProxy() != null) {
            throwable = ThrowableProxyUtil.asString(event.getThrowableProxy());
        }
        current.accept(new StoredLogEvent(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel() == null ? "INFO" : event.getLevel().toString(),
                loggerName,
                event.getThreadName(),
                event.getFormattedMessage(),
                throwable
        ));
    }

    /**
     * Événement à persister.
     *
     * @param timestamp instant
     * @param level     niveau SLF4J
     * @param logger    nom du logger
     * @param thread    fil
     * @param message   message
     * @param throwable stack éventuelle
     */
    public record StoredLogEvent(
            Instant timestamp,
            String level,
            String logger,
            String thread,
            String message,
            String throwable
    ) {
    }
}
