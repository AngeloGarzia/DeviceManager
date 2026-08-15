-- Signatures dessinées (admin + technicien) et lien vers le bon d'intervention.

ALTER TABLE fit_ligne
    ADD COLUMN intervention_id BIGINT NULL,
    ADD COLUMN signature_admin LONGTEXT NULL,
    ADD COLUMN signature_technicien LONGTEXT NULL,
    ADD COLUMN signataire_admin_nom VARCHAR(120) NULL,
    ADD COLUMN signataire_technicien_nom VARCHAR(120) NULL;

ALTER TABLE fit_ligne
    ADD CONSTRAINT fk_fit_ligne_intervention
        FOREIGN KEY (intervention_id) REFERENCES intervention (id);

CREATE INDEX idx_fit_ligne_intervention ON fit_ligne (intervention_id);
CREATE INDEX idx_fit_mas_unique_lookup ON fit (atelier_id, mas_id);
