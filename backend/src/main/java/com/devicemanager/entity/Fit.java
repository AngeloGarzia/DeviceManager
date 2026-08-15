package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiche inventaire / intervention technique (FIT) d'une machine à sous.
 * Correspond à l'en-tête du formulaire réglementaire (modèle n° 34).
 */
@Entity
@Table(
        name = "fit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fit_atelier_numero_machine",
                columnNames = {"atelier_id", "numero_machine_casino"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fit_atelier"))
    private Atelier atelier;

    /** MAS liée si la fiche est rattachée au référentiel applicatif. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", foreignKey = @ForeignKey(name = "fk_fit_mas"))
    private Mas mas;

    @Column(name = "casino_nom", length = 160)
    private String casinoNom;

    /** N° machine du casino. */
    @Column(name = "numero_machine_casino", nullable = false, length = 80)
    private String numeroMachineCasino;

    @Column(name = "date_mise_en_service")
    private LocalDate dateMiseEnService;

    @Column(length = 120)
    private String marque;

    /** Type / modèle commercial de la machine. */
    @Column(name = "type_machine", length = 120)
    private String typeMachine;

    @Column(name = "numero_serie_machine", length = 120)
    private String numeroSerieMachine;

    /** N° de série du lecteur de carte de paiement (état courant / d'origine). */
    @Column(name = "numero_serie_lecteur", length = 120)
    private String numeroSerieLecteur;

    @Column(name = "date_cessation")
    private LocalDate dateCessation;

    @Column(name = "destination_machine_usagee", length = 255)
    private String destinationMachineUsagee;

    /** Référence du formulaire (ex. modèle n° 34). */
    @Column(name = "modele_numero", nullable = false, length = 40)
    @Builder.Default
    private String modeleNumero = "34";

    @Column(name = "reference_legale", length = 255)
    @Builder.Default
    private String referenceLegale = "Article 67-29 de l'arrêté du 14 Mai 2007";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "fit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateOperation DESC, id DESC")
    @Builder.Default
    private List<FitLigne> lignes = new ArrayList<>();

    public void addLigne(FitLigne ligne) {
        lignes.add(ligne);
        ligne.setFit(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (modeleNumero == null || modeleNumero.isBlank()) {
            modeleNumero = "34";
        }
        if (referenceLegale == null || referenceLegale.isBlank()) {
            referenceLegale = "Article 67-29 de l'arrêté du 14 Mai 2007";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
