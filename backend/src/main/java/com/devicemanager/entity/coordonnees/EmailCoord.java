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
@Table(name = "email_coord")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailCoord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordonnees_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_email_coord_coordonnees"))
    private Coordonnees coordonnees;

    @Column(nullable = false, length = 160)
    private String valeur;

    @Column(nullable = false)
    @Builder.Default
    private boolean principal = false;
}
