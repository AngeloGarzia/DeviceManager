package com.devicemanager.entity.coordonnees;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensemble de coordonnées d'un atelier : adresse, e-mails, téléphones et réseaux sociaux.
 * Entité autonome liée en {@code OneToOne} à {@link com.devicemanager.entity.Atelier}.
 */
@Entity
@Table(name = "coordonnees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coordonnees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @Builder.Default
    private AdressePostale adresse = new AdressePostale();

    @OneToMany(mappedBy = "coordonnees", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<EmailCoord> emails = new ArrayList<>();

    @OneToMany(mappedBy = "coordonnees", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<TelephoneCoord> telephones = new ArrayList<>();

    @OneToMany(mappedBy = "coordonnees", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ReseauSocial> reseauxSociaux = new ArrayList<>();

    public void clearEmails() {
        emails.clear();
    }

    public void addEmail(EmailCoord email) {
        email.setCoordonnees(this);
        emails.add(email);
    }

    public void clearTelephones() {
        telephones.clear();
    }

    public void addTelephone(TelephoneCoord telephone) {
        telephone.setCoordonnees(this);
        telephones.add(telephone);
    }

    public void clearReseauxSociaux() {
        reseauxSociaux.clear();
    }

    public void addReseauSocial(ReseauSocial reseau) {
        reseau.setCoordonnees(this);
        reseauxSociaux.add(reseau);
    }
}
