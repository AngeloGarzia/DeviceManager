package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Demande de commande de pièces déposée par un technicien pour un atelier.
 * Contient un message libre et une ou plusieurs lignes (pièce + quantité).
 */
@Entity
@Table(name = "commande")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Technicien ayant soumis la demande. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "technicien_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_commande_technicien"))
    private User technicien;

    /** Nom du technicien dénormalisé pour l'historique et les e-mails. */
    @Column(name = "technicien_nom", nullable = false, length = 120)
    private String technicienNom;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    /** Horodatage de la validation admin (null pour l'historique ancien). */
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    /** Horodatage de la réception physique (null pour l'historique ancien). */
    @Column(name = "date_reception")
    private LocalDateTime dateReception;

    /** Statut du cycle de vie (ex. {@code PENDING}, {@code VALIDATED}, {@code RECEIVED}). */
    @Column(nullable = false, length = 30)
    private String status;

    /** Clé de stockage du devis (PDF ou image, après validation). */
    @Column(name = "devis_file_key", length = 512)
    private String devisFileKey;

    @Column(name = "devis_file_url", length = 1024)
    private String devisFileUrl;

    @Column(name = "devis_original_name", length = 255)
    private String devisOriginalName;

    @Column(name = "devis_content_type", length = 120)
    private String devisContentType;

    @Column(name = "devis_file_size")
    private Long devisFileSize;

    @Column(name = "devis_uploaded_at")
    private LocalDateTime devisUploadedAt;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CommandeLigne> lignes = new ArrayList<>();

    /** Atelier concerné — périmètre multi-tenant de la commande. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_commande_atelier"))
    private Atelier atelier;

    public void addLigne(CommandeLigne ligne) {
        lignes.add(ligne);
        ligne.setCommande(this);
    }

    @PrePersist
    void onCreate() {
        if (dateDemande == null) {
            dateDemande = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
