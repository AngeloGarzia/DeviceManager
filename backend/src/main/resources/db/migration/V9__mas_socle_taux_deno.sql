-- Référentiel des dénominations MAS + champs socle / taux / deno sur mas.
CREATE TABLE deno (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    valeur  DECIMAL(10, 4) NOT NULL,
    label   VARCHAR(40)    NOT NULL,
    CONSTRAINT uk_deno_valeur UNIQUE (valeur),
    CONSTRAINT uk_deno_label UNIQUE (label),
    CONSTRAINT chk_deno_valeur_positive CHECK (valeur > 0)
);

INSERT INTO deno (valeur, label) VALUES
    (0.0100, '0,01 €'),
    (0.0200, '0,02 €'),
    (0.0500, '0,05 €'),
    (0.1000, '0,10 €'),
    (0.2000, '0,20 €'),
    (0.5000, '0,50 €'),
    (1.0000, '1,00 €'),
    (2.0000, '2,00 €');

ALTER TABLE mas
    ADD COLUMN numero_socle VARCHAR(80) NULL,
    ADD COLUMN taux_redistribution DECIMAL(6, 2) NULL,
    ADD COLUMN deno_id BIGINT NULL,
    ADD CONSTRAINT fk_mas_deno FOREIGN KEY (deno_id) REFERENCES deno (id),
    ADD CONSTRAINT chk_mas_taux_redistribution
        CHECK (taux_redistribution IS NULL OR (taux_redistribution >= 0 AND taux_redistribution <= 100));

CREATE INDEX idx_mas_deno ON mas (deno_id);
