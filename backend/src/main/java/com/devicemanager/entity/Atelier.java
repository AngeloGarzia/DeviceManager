package com.devicemanager.entity;

import com.devicemanager.entity.coordonnees.Coordonnees;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "atelier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atelier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nom;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "casino_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_atelier_casino"))
    private Casino casino;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordonnees_id", foreignKey = @ForeignKey(name = "fk_atelier_coordonnees"))
    private Coordonnees coordonnees;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "atelier_responsable",
            joinColumns = @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_atelier_resp_atelier")),
            inverseJoinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_atelier_resp_user"))
    )
    @Builder.Default
    private Set<User> responsables = new HashSet<>();
}
