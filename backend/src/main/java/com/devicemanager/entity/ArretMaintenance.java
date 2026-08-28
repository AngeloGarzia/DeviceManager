package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Arrêt temporaire d'une MAS pour maintenance.
 * Clôturé lorsque {@link #dateHeureReprise} est renseignée.
 */
@Entity
@Table(name = "arret_maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArretMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_arret_maintenance_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_arret_maintenance_mas"))
    private Mas mas;

    @Column(name = "admin_username", nullable = false, length = 120)
    private String adminUsername;

    @Column(name = "admin_display_name", length = 160)
    private String adminDisplayName;

    @Column(name = "date_heure_arret", nullable = false)
    private LocalDateTime dateHeureArret;

    @Column(name = "date_heure_reprise")
    private LocalDateTime dateHeureReprise;

    @Column(name = "motif_arret", nullable = false, length = 500)
    private String motifArret;

    @Column(name = "registre_technique_a_jour", nullable = false)
    @Builder.Default
    private boolean registreTechniqueAJour = false;

    @Lob
    @Column(name = "signature_arret", columnDefinition = "LONGTEXT")
    private String signatureArret;

    @Lob
    @Column(name = "signature_redemarrage", columnDefinition = "LONGTEXT")
    private String signatureRedemarrage;

    @Column(name = "signataire_arret_nom", length = 120)
    private String signataireArretNom;

    @Column(name = "signataire_redemarrage_nom", length = 120)
    private String signataireRedemarrageNom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isActif() {
        return dateHeureReprise == null;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (dateHeureArret == null) {
            dateHeureArret = LocalDateTime.now();
        }
    }
}
