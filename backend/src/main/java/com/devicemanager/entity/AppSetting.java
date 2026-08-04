package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Paramètre applicatif clé-valeur (SMTP, clés API, préférences globales).
 * La clé sert d'identifiant primaire ; les valeurs sensibles sont masquées côté API.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    /** Identifiant unique du paramètre (ex. {@code mail.smtp.host}). */
    @Id
    @Column(name = "setting_key", length = 80)
    private String settingKey;

    @Column(name = "setting_value", length = 1000)
    private String settingValue;

    /** Libellé affiché dans l'interface d'administration. */
    @Column(nullable = false, length = 160)
    private String label;

    /** Catégorie de regroupement (ex. mail, ai, general). */
    @Column(nullable = false, length = 40)
    private String category;

    /** Indique si la valeur doit être masquée à l'affichage (mot de passe, clé API). */
    @Column(name = "secret_value", nullable = false)
    private boolean secretValue;
}
