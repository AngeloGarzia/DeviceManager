-- Visites quadritrimestrielles : une visite SFM × marque tous les 4 mois.
CREATE TABLE visite_quadritrimestrelle (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id  BIGINT       NOT NULL,
    sfm_id      BIGINT       NOT NULL,
    marque_id   BIGINT       NOT NULL,
    date_visite DATE         NOT NULL,
    notes       VARCHAR(2000) NULL,
    created_by  VARCHAR(120) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_visite_quadri_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_visite_quadri_sfm FOREIGN KEY (sfm_id) REFERENCES sfm (id),
    CONSTRAINT fk_visite_quadri_marque FOREIGN KEY (marque_id) REFERENCES marque_mas (id)
);

CREATE INDEX idx_visite_quadri_atelier_sfm_marque_date
    ON visite_quadritrimestrelle (atelier_id, sfm_id, marque_id, date_visite DESC);
