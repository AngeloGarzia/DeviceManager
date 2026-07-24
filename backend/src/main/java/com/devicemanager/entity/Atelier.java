package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

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
}
