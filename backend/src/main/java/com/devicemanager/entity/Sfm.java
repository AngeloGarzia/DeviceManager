package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SFM (Service Fournisseur Maintenance) — fournisseur de pièces pour un atelier.
 * Peut couvrir plusieurs marques et disposer de plusieurs contacts.
 * Le nom est unique par atelier.
 */
@Entity
@Table(
        name = "sfm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sfm_nom_atelier",
                columnNames = {"nom", "atelier_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sfm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nom;

    /** Conservé pour compatibilité / recherche : synchronisé avec le 1er contact. */
    @Column(nullable = false, length = 120)
    private String responsable;

    @Column(nullable = false, length = 40)
    private String telephone;

    @Column(nullable = false, length = 160)
    private String email;

    /**
     * Contacts rattachés (N–N) — les techniciens SFM peuvent être liés à plusieurs SFM.
     */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "sfm_sfm_contact",
            joinColumns = @JoinColumn(name = "sfm_id", foreignKey = @ForeignKey(name = "fk_sfm_sfm_contact_sfm")),
            inverseJoinColumns = @JoinColumn(name = "contact_id", foreignKey = @ForeignKey(name = "fk_sfm_sfm_contact_contact"))
    )
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    @Builder.Default
    private List<SfmContact> contacts = new ArrayList<>();

    /**
     * Marques couvertes par ce SFM — les MAS liées sont celles de ces marques.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sfm_marque",
            joinColumns = @JoinColumn(name = "sfm_id", foreignKey = @ForeignKey(name = "fk_sfm_marque_sfm")),
            inverseJoinColumns = @JoinColumn(name = "marque_id", foreignKey = @ForeignKey(name = "fk_sfm_marque_marque"))
    )
    @BatchSize(size = 50)
    @Builder.Default
    private Set<MarqueMas> marques = new HashSet<>();

    /** Atelier propriétaire — périmètre multi-tenant. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_sfm_atelier"))
    private Atelier atelier;

    public void addContact(SfmContact contact) {
        if (contacts.contains(contact)) {
            return;
        }
        contacts.add(contact);
        if (contact.getSfms() == null) {
            contact.setSfms(new HashSet<>());
        }
        contact.getSfms().add(this);
    }

    public void removeContact(SfmContact contact) {
        contacts.remove(contact);
        if (contact.getSfms() != null) {
            contact.getSfms().remove(this);
        }
    }

    public void syncPrimaryContactFields() {
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        SfmContact first = contacts.get(0);
        this.responsable = first.getNom();
        this.telephone = first.getTelephone();
        this.email = first.getEmail();
    }
}
