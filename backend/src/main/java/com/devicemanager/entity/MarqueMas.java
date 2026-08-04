package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Marque de pièce du catalogue MAS (référentiel global).
 * Identifiée par un code court et un libellé unique.
 */
@Entity
@Table(name = "marque_mas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarqueMas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique (ex. {@code IGT}). */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    /** Libellé affiché (ex. {@code International Game Technology}). */
    @Column(nullable = false, unique = true, length = 120)
    private String label;
}
