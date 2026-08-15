package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Dénomination monétaire d'une MAS (référentiel global).
 * Exemples : 0,01 €, 0,50 €.
 */
@Entity
@Table(name = "deno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Valeur numérique de la dénomination (ex. {@code 0.01}). */
    @Column(nullable = false, unique = true, precision = 10, scale = 4)
    private BigDecimal valeur;

    /** Libellé affiché (ex. {@code 0,01 €}). */
    @Column(nullable = false, unique = true, length = 40)
    private String label;
}
