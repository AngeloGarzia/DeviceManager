package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Alerte d'incohérence de prix sur une pièce.
 */
@Entity
@Table(name = "device_prix_alerte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePrixAlerte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prix_alerte_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prix_alerte_device"))
    private Device device;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prix_alerte_obs"))
    private DevicePrixObservation observation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrixAlerteSeverity severity;

    @Column(name = "signals_json", columnDefinition = "TEXT")
    private String signalsJson;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_payload", columnDefinition = "TEXT")
    private String aiPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PrixAlerteStatus status = PrixAlerteStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ack_by", length = 80)
    private String ackBy;

    @Column(name = "ack_at")
    private LocalDateTime ackAt;
}
