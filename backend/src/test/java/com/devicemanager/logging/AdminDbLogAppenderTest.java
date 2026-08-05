package com.devicemanager.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminDbLogAppenderTest {

    @AfterEach
    void tearDown() {
        AdminDbLogAppender.setSink(null);
    }

    @Test
    void forwardsEventsToSink() {
        List<AdminDbLogAppender.StoredLogEvent> captured = new ArrayList<>();
        AdminDbLogAppender.setSink(captured::add);

        AdminDbLogAppender appender = new AdminDbLogAppender();
        appender.start();

        ch.qos.logback.classic.LoggerContext ctx = new ch.qos.logback.classic.LoggerContext();
        ch.qos.logback.classic.Logger logger = ctx.getLogger("com.devicemanager.test");
        ch.qos.logback.classic.spi.LoggingEvent event = new ch.qos.logback.classic.spi.LoggingEvent(
                ch.qos.logback.classic.Logger.FQCN,
                logger,
                ch.qos.logback.classic.Level.INFO,
                "persisted",
                null,
                null
        );
        appender.append(event);

        assertEquals(1, captured.size());
        assertEquals("persisted", captured.getFirst().message());
        appender.stop();
    }

    @Test
    void ignoresWriterLogger() {
        AtomicReference<AdminDbLogAppender.StoredLogEvent> ref = new AtomicReference<>();
        AdminDbLogAppender.setSink(ref::set);

        AdminDbLogAppender appender = new AdminDbLogAppender();
        ch.qos.logback.classic.LoggerContext ctx = new ch.qos.logback.classic.LoggerContext();
        ch.qos.logback.classic.Logger logger = ctx.getLogger("com.devicemanager.logging.AppLogDbWriter");
        ch.qos.logback.classic.spi.LoggingEvent event = new ch.qos.logback.classic.spi.LoggingEvent(
                ch.qos.logback.classic.Logger.FQCN,
                logger,
                ch.qos.logback.classic.Level.INFO,
                "skip-me",
                null,
                null
        );
        appender.append(event);
        assertNull(ref.get());
    }
}
