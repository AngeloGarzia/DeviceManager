-- Bons d'intervention : consommation de pièces détachées archivée.
CREATE TABLE intervention (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero          VARCHAR(40)  NOT NULL,
    date_intervention DATETIME   NOT NULL,
    technicien_id   BIGINT       NOT NULL,
    technicien_nom  VARCHAR(120) NOT NULL,
    atelier_id      BIGINT       NOT NULL,
    emplacement     VARCHAR(200) NULL,
    machine_mas     VARCHAR(120) NULL,
    motif           VARCHAR(500) NOT NULL,
    diagnostic      VARCHAR(2000) NULL,
    travaux         VARCHAR(2000) NOT NULL,
    observations    VARCHAR(2000) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_intervention_numero UNIQUE (numero),
    CONSTRAINT fk_intervention_technicien
        FOREIGN KEY (technicien_id) REFERENCES users (id),
    CONSTRAINT fk_intervention_atelier
        FOREIGN KEY (atelier_id) REFERENCES atelier (id)
);

CREATE TABLE intervention_ligne (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    intervention_id  BIGINT       NOT NULL,
    device_id        BIGINT       NOT NULL,
    piece_nom        VARCHAR(120) NOT NULL,
    piece_reference  VARCHAR(80)  NULL,
    quantite         INT          NOT NULL,
    stock_avant      INT          NOT NULL,
    stock_apres      INT          NOT NULL,
    CONSTRAINT fk_intervention_ligne_intervention
        FOREIGN KEY (intervention_id) REFERENCES intervention (id) ON DELETE CASCADE,
    CONSTRAINT fk_intervention_ligne_device
        FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT chk_intervention_ligne_quantite CHECK (quantite > 0)
);

CREATE INDEX idx_intervention_atelier_date ON intervention (atelier_id, date_intervention DESC);
CREATE INDEX idx_intervention_ligne_intervention ON intervention_ligne (intervention_id);
