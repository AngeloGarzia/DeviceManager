package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Contact SFM (fiche personne partageable).
 * Peut être rattaché à plusieurs SFM (N–N) et marqué « Technicien SFM ».
 */
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

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, length = 40)
    private String telephone;

    @Column(nullable = false, length = 160)
    private String email;

    /** Si true (défaut), ce contact reçoit les e-mails de commande validée. */
    @Column(name = "receive_order_mails")
    @Builder.Default
    private Boolean receiveOrderMails = Boolean.TRUE;

    /** Technicien SFM — peut appartenir à plusieurs SFM. */
    @Column(name = "technicien_sfm", nullable = false)
    @Builder.Default
    private boolean technicienSfm = false;

    @ManyToMany(mappedBy = "contacts")
    @Builder.Default
    private Set<Sfm> sfms = new HashSet<>();

    public boolean isReceiveOrderMails() {
        return receiveOrderMails == null || receiveOrderMails;
    }
}
