package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Référence MAS (Matériel After-Sales) d'un atelier.
 * Numéro unique par atelier, associé à une marque du catalogue.
 */
@Entity
@Table(
        name = "mas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mas_numero_atelier",
                columnNames = {"numero", "atelier_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String numero;

    /** Numéro de socle / emplacement physique de la machine. */
    @Column(name = "numero_socle", length = 80)
    private String numeroSocle;

    /** Taux de redistribution en pourcentage (0–100). */
    @Column(name = "taux_redistribution", precision = 6, scale = 2)
    private BigDecimal tauxRedistribution;

    @Column(name = "date_mise_en_service")
    private LocalDate dateMiseEnService;

    /** Type de machine : machine à sous, poker, etc. */
    @Column(name = "type_machine", length = 120)
    private String typeMachine;

    @Column(name = "numero_serie", length = 120)
    private String numeroSerie;

    @Column(name = "date_cessation")
    private LocalDate dateCessation;

    @Column(name = "destination_machine_usagee", length = 255)
    private String destinationMachineUsagee;

    /** Bon de destruction (PDF ou image), si statut = DETRUITE. */
    @Column(name = "destruction_file_key", length = 512)
    private String destructionFileKey;

    @Column(name = "destruction_file_url", length = 1024)
    private String destructionFileUrl;

    @Column(name = "destruction_original_name", length = 255)
    private String destructionOriginalName;

    @Column(name = "destruction_content_type", length = 120)
    private String destructionContentType;

    @Column(name = "destruction_file_size")
    private Long destructionFileSize;

    @Column(name = "destruction_uploaded_at")
    private LocalDateTime destructionUploadedAt;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "marque_id",
            foreignKey = @ForeignKey(name = "fk_mas_marque"))
    private MarqueMas marque;

    /** Dénomination monétaire héritée du référentiel {@link Deno}. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "deno_id",
            foreignKey = @ForeignKey(name = "fk_mas_deno"))
    private Deno deno;

    /**
     * Si vrai, la MAS est multi-dénomination : pas de {@link #deno} unique,
     * l'affichage API utilise le libellé « MultiDéno ».
     */
    @Column(name = "multi_deno", nullable = false)
    @Builder.Default
    private boolean multiDeno = false;

    /**
     * Statut d'exploitation (utilisée, en réserve, vendue, détruite).
     * {@link #utilise} reste synchronisé pour compatibilité.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 40)
    @Builder.Default
    private MasStatut statut = MasStatut.UTILISEE;

    /** Indique si la machine est en exploitation ({@link MasStatut#UTILISEE}). */
    @Column(nullable = false)
    private boolean utilise;

    /** Atelier propriétaire — périmètre multi-tenant. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_mas_atelier"))
    private Atelier atelier;

    public void applyStatut(MasStatut next) {
        this.statut = next != null ? next : MasStatut.UTILISEE;
        this.utilise = this.statut.isUtilisee();
    }

    @PrePersist
    @PreUpdate
    void syncUtiliseFromStatut() {
        if (statut == null) {
            statut = utilise ? MasStatut.UTILISEE : MasStatut.EN_RESERVE;
        }
        utilise = statut.isUtilisee();
    }
}
