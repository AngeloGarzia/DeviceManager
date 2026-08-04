package com.devicemanager.entity.coordonnees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lien vers un réseau social rattaché à un bloc {@link Coordonnees}.
 */
@Entity
@Table(name = "reseau_social")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReseauSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordonnees_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reseau_social_coordonnees"))
    private Coordonnees coordonnees;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeReseauSocial type;

    @Column(nullable = false, length = 255)
    private String url;
}
