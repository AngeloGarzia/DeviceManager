-- Historique des prix pièces (issus des devis) + alertes d'incohérence.
ALTER TABLE device
    ADD COLUMN last_unit_price_ht   DECIMAL(12, 2) NULL,
    ADD COLUMN last_unit_price_at   DATETIME       NULL;

CREATE TABLE device_prix_observation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id          BIGINT         NOT NULL,
    device_id           BIGINT         NOT NULL,
    commande_id         BIGINT         NULL,
    source              VARCHAR(40)    NOT NULL,
    unit_price_ht       DECIMAL(12, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL DEFAULT 'EUR',
    quantity_on_quote   INT            NULL,
    devis_designation   VARCHAR(255)   NULL,
    devis_reference     VARCHAR(120)   NULL,
    observed_at         DATETIME       NOT NULL,
    confirmed_at        DATETIME       NOT NULL,
    confirmed_by        VARCHAR(80)    NOT NULL,
    invalidated         BOOLEAN        NOT NULL DEFAULT FALSE,
    invalidated_reason  VARCHAR(255)   NULL,
    CONSTRAINT fk_prix_obs_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_prix_obs_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_prix_obs_commande FOREIGN KEY (commande_id) REFERENCES commande (id),
    CONSTRAINT chk_prix_obs_source CHECK (source IN ('DEVIS')),
    CONSTRAINT chk_prix_obs_positive CHECK (unit_price_ht >= 0)
);

CREATE INDEX idx_prix_obs_device_observed ON device_prix_observation (atelier_id, device_id, observed_at);
CREATE INDEX idx_prix_obs_commande ON device_prix_observation (commande_id);

CREATE TABLE device_prix_alerte (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id      BIGINT       NOT NULL,
    device_id       BIGINT       NOT NULL,
    observation_id  BIGINT       NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    signals_json    TEXT         NULL,
    ai_summary      TEXT         NULL,
    ai_payload      TEXT         NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME     NOT NULL,
    ack_by          VARCHAR(80)  NULL,
    ack_at          DATETIME     NULL,
    CONSTRAINT fk_prix_alerte_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_prix_alerte_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_prix_alerte_obs FOREIGN KEY (observation_id) REFERENCES device_prix_observation (id),
    CONSTRAINT chk_prix_alerte_severity CHECK (severity IN ('WATCH', 'ALERT')),
    CONSTRAINT chk_prix_alerte_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'DISMISSED'))
);

CREATE INDEX idx_prix_alerte_open ON device_prix_alerte (atelier_id, status, created_at);
