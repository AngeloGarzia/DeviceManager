-- Lien fort intervention → MAS pour le suivi technique.
ALTER TABLE intervention
    ADD COLUMN mas_id BIGINT NULL,
    ADD CONSTRAINT fk_intervention_mas FOREIGN KEY (mas_id) REFERENCES mas (id);

CREATE INDEX idx_intervention_atelier_mas ON intervention (atelier_id, mas_id);
