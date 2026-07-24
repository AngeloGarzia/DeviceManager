package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, unique = true, length = 120)
    private String label;
}
