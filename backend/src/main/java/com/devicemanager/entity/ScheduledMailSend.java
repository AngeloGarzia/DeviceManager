package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trace d'un e-mail de rappel planifié déjà envoyé (idempotence journalière).
 */
@Entity
@Table(name = "scheduled_mail_send", uniqueConstraints = @UniqueConstraint(
        name = "uk_scheduled_mail_send",
        columnNames = {"job_key", "period_key", "recipient"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMailSend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_key", nullable = false, length = 64)
    private String jobKey;

    /** Jour local d'envoi, ex. {@code 2026-09-17}. */
    @Column(name = "period_key", nullable = false, length = 32)
    private String periodKey;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
