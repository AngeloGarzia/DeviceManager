package com.devicemanager.entity.coordonnees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "telephone_coord")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelephoneCoord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordonnees_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_telephone_coord_coordonnees"))
    private Coordonnees coordonnees;

    @Column(nullable = false, length = 40)
    private String valeur;

    @Column(length = 40)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private boolean principal = false;
}
