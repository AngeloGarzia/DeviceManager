package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ligne d'historique d'une FIT : opération ayant affecté la machine.
 * Chaque enregistrement doit être signé (dessin) par un admin et un technicien.
 */
@Entity
@Table(name = "fit_ligne")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FitLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fit_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fit_ligne_fit"))
    private Fit fit;

    /** Bon d'intervention à l'origine de la ligne (consommation de pièces), optionnel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id",
            foreignKey = @ForeignKey(name = "fk_fit_ligne_intervention"))
    private Intervention intervention;

    @Column(name = "date_operation", nullable = false)
    private LocalDate dateOperation;

    @Column(name = "numero_socle", length = 80)
    private String numeroSocle;

    @Column(name = "numero_emplacement", length = 80)
    private String numeroEmplacement;

    @Column(name = "numero_serie_lecteur", length = 120)
    private String numeroSerieLecteur;

    @Column(name = "taux_redistribution", precision = 6, scale = 2)
    private BigDecimal tauxRedistribution;

    /** Valeur unitaire des mises (€), éventuellement héritée de {@link Deno}. */
    @Column(name = "valeur_unitaire_mises", precision = 10, scale = 4)
    private BigDecimal valeurUnitaireMises;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deno_id", foreignKey = @ForeignKey(name = "fk_fit_ligne_deno"))
    private Deno deno;

    @Column(name = "motif_nature_operations", nullable = false, length = 2000)
    private String motifNatureOperations;

    /**
     * Signature manuscrite (data URL image) du directeur / admin.
     */
    @Lob
    @Column(name = "signature_admin", columnDefinition = "LONGTEXT")
    private String signatureAdmin;

    /**
     * Signature manuscrite (data URL image) du technicien.
     */
    @Lob
    @Column(name = "signature_technicien", columnDefinition = "LONGTEXT")
    private String signatureTechnicien;

    @Column(name = "signataire_admin_nom", length = 120)
    private String signataireAdminNom;

    @Column(name = "signataire_technicien_nom", length = 120)
    private String signataireTechnicienNom;

    /** Compatibilité modèle papier : true dès qu'une signature admin est présente. */
    @Column(name = "signature_directeur", nullable = false)
    @Builder.Default
    private boolean signatureDirecteur = false;

    @Column(name = "signataire_nom", length = 120)
    private String signataireNom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        syncLegacySignatureFlags();
    }

    @PreUpdate
    void onUpdate() {
        syncLegacySignatureFlags();
    }

    private void syncLegacySignatureFlags() {
        boolean signed = signatureAdmin != null && !signatureAdmin.isBlank();
        signatureDirecteur = signed;
        if (signataireNom == null || signataireNom.isBlank()) {
            signataireNom = signataireAdminNom;
        }
    }
}
