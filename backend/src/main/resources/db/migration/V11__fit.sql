-- Fiche inventaire / intervention technique (FIT) selon modèle réglementaire MAS.
-- En-tête = identification machine ; lignes = historique des opérations.

CREATE TABLE fit (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id                  BIGINT       NOT NULL,
    mas_id                      BIGINT       NULL,
    casino_nom                  VARCHAR(160) NULL,
    numero_machine_casino       VARCHAR(80)  NOT NULL,
    date_mise_en_service        DATE         NULL,
    marque                      VARCHAR(120) NULL,
    type_machine                VARCHAR(120) NULL,
    numero_serie_machine        VARCHAR(120) NULL,
    numero_serie_lecteur        VARCHAR(120) NULL,
    date_cessation              DATE         NULL,
    destination_machine_usagee  VARCHAR(255) NULL,
    modele_numero               VARCHAR(40)  NOT NULL DEFAULT '34',
    reference_legale            VARCHAR(255) NULL DEFAULT 'Article 67-29 de l''arrêté du 14 Mai 2007',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NULL,
    CONSTRAINT uk_fit_atelier_numero_machine UNIQUE (atelier_id, numero_machine_casino),
    CONSTRAINT fk_fit_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_fit_mas FOREIGN KEY (mas_id) REFERENCES mas (id)
);

CREATE TABLE fit_ligne (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    fit_id                      BIGINT       NOT NULL,
    date_operation              DATE         NOT NULL,
    numero_socle                VARCHAR(80)  NULL,
    numero_emplacement          VARCHAR(80)  NULL,
    numero_serie_lecteur        VARCHAR(120) NULL,
    taux_redistribution         DECIMAL(6, 2) NULL,
    valeur_unitaire_mises       DECIMAL(10, 4) NULL,
    deno_id                     BIGINT       NULL,
    motif_nature_operations     VARCHAR(2000) NOT NULL,
    signature_directeur         BOOLEAN      NOT NULL DEFAULT FALSE,
    signataire_nom              VARCHAR(120) NULL,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fit_ligne_fit FOREIGN KEY (fit_id) REFERENCES fit (id) ON DELETE CASCADE,
    CONSTRAINT fk_fit_ligne_deno FOREIGN KEY (deno_id) REFERENCES deno (id),
    CONSTRAINT chk_fit_ligne_taux
        CHECK (taux_redistribution IS NULL OR (taux_redistribution >= 0 AND taux_redistribution <= 100)),
    CONSTRAINT chk_fit_ligne_valeur
        CHECK (valeur_unitaire_mises IS NULL OR valeur_unitaire_mises > 0)
);

CREATE INDEX idx_fit_atelier ON fit (atelier_id);
CREATE INDEX idx_fit_mas ON fit (mas_id);
CREATE INDEX idx_fit_ligne_fit_date ON fit_ligne (fit_id, date_operation DESC);
