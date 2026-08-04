package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Groupe organisationnel regroupant des casinos et leurs utilisateurs.
 * Niveau le plus haut de la hiérarchie multi-tenant.
 */
@Entity
@Table(name = "groupe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Groupe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String nom;

    /** Casinos appartenant à ce groupe. */
    @OneToMany(mappedBy = "groupe")
    @Builder.Default
    private List<Casino> casinos = new ArrayList<>();
}
