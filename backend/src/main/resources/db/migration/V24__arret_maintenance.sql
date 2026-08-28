-- Arrêts temporaires « maintenance » sur les MAS (signatures si registre technique à jour).

CREATE TABLE arret_maintenance (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id                  BIGINT       NOT NULL,
    mas_id                      BIGINT       NOT NULL,
    admin_username              VARCHAR(120) NOT NULL,
    admin_display_name          VARCHAR(160) NULL,
    date_heure_arret            DATETIME     NOT NULL,
    date_heure_reprise          DATETIME     NULL,
    motif_arret                 VARCHAR(500) NOT NULL,
    registre_technique_a_jour   BOOLEAN      NOT NULL DEFAULT FALSE,
    signature_arret             LONGTEXT     NULL,
    signature_redemarrage       LONGTEXT     NULL,
    signataire_arret_nom        VARCHAR(120) NULL,
    signataire_redemarrage_nom  VARCHAR(120) NULL,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_arret_maintenance_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_arret_maintenance_mas FOREIGN KEY (mas_id) REFERENCES mas (id)
);

CREATE INDEX idx_arret_maintenance_atelier_actif
    ON arret_maintenance (atelier_id, date_heure_reprise, date_heure_arret);

CREATE INDEX idx_arret_maintenance_mas_actif
    ON arret_maintenance (mas_id, date_heure_reprise);
