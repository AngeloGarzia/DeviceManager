-- Interventions techniques libres sur MAS (1 ligne = 1 MAS).
-- Distinctes du bon d'intervention (table intervention) qui consomme des pièces.

CREATE TABLE interventions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    visite_groupe_id        VARCHAR(36)  NOT NULL,
    atelier_id              BIGINT       NOT NULL,
    mas_id                  BIGINT       NOT NULL,
    date_intervention       DATETIME     NOT NULL,
    technicien_id           BIGINT       NOT NULL,
    technicien_nom          VARCHAR(120) NOT NULL,
    emplacement             VARCHAR(200) NULL,
    motif                   VARCHAR(500) NOT NULL,
    diagnostic              VARCHAR(2000) NULL,
    travaux                 VARCHAR(2000) NOT NULL,
    observations            VARCHAR(2000) NULL,
    fit_id                  BIGINT       NULL,
    fit_ligne_id            BIGINT       NULL,
    commande_id             BIGINT       NULL,
    bon_intervention_id     BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interventions_atelier
        FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_interventions_mas
        FOREIGN KEY (mas_id) REFERENCES mas (id),
    CONSTRAINT fk_interventions_technicien
        FOREIGN KEY (technicien_id) REFERENCES users (id),
    CONSTRAINT fk_interventions_fit
        FOREIGN KEY (fit_id) REFERENCES fit (id),
    CONSTRAINT fk_interventions_fit_ligne
        FOREIGN KEY (fit_ligne_id) REFERENCES fit_ligne (id),
    CONSTRAINT fk_interventions_commande
        FOREIGN KEY (commande_id) REFERENCES commande (id),
    CONSTRAINT fk_interventions_bon
        FOREIGN KEY (bon_intervention_id) REFERENCES intervention (id)
);

CREATE INDEX idx_interventions_atelier_date ON interventions (atelier_id, date_intervention DESC);
CREATE INDEX idx_interventions_mas_date ON interventions (mas_id, date_intervention DESC);
CREATE INDEX idx_interventions_visite ON interventions (visite_groupe_id);
CREATE INDEX idx_interventions_commande ON interventions (commande_id);
CREATE INDEX idx_interventions_bon ON interventions (bon_intervention_id);
