package com.devicemanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entrée de journal SLF4J persistée en base pour consultation admin.
 */
@Entity
@Table(name = "app_log", indexes = {
        @Index(name = "idx_app_log_created", columnList = "created_at"),
        @Index(name = "idx_app_log_level", columnList = "level")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(name = "logger_name", nullable = false, length = 255)
    private String loggerName;

    @Column(name = "thread_name", length = 120)
    private String threadName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String throwable;
}
