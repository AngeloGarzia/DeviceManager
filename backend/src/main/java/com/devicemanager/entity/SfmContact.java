package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sfm_contact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SfmContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sfm_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sfm_contact_sfm"))
    private Sfm sfm;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, length = 40)
    private String telephone;

    @Column(nullable = false, length = 160)
    private String email;
}
