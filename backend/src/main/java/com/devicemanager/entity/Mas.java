package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "marque_id",
            foreignKey = @ForeignKey(name = "fk_mas_marque"))
    private MarqueMas marque;

    /** Indique si cette référence est encore utilisée en exploitation. */
    @Column(nullable = false)
    private boolean utilise;

    /** Atelier propriétaire — périmètre multi-tenant. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_mas_atelier"))
    private Atelier atelier;
}
