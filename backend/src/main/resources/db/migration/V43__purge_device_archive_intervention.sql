-- Purge pièce Super Admin : archiver les bons, conserver l'historique (stock / lignes).
-- Les FK device deviennent nullable + ON DELETE SET NULL pour détacher sans casser l'héritage.

ALTER TABLE intervention
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archived_at DATETIME NULL,
    ADD COLUMN archived_reason VARCHAR(255) NULL;

CREATE INDEX idx_intervention_atelier_archived ON intervention (atelier_id, archived);

-- Lignes de bon : garder piece_nom, détacher la pièce
ALTER TABLE intervention_ligne DROP FOREIGN KEY fk_intervention_ligne_device;
ALTER TABLE intervention_ligne MODIFY device_id BIGINT NULL;
ALTER TABLE intervention_ligne
    ADD CONSTRAINT fk_intervention_ligne_device
        FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE SET NULL;

-- Mouvements de stock : garder piece_nom / référence
ALTER TABLE stock_mouvement DROP FOREIGN KEY fk_stock_mouvement_device;
ALTER TABLE stock_mouvement MODIFY device_id BIGINT NULL;
ALTER TABLE stock_mouvement
    ADD CONSTRAINT fk_stock_mouvement_device
        FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE SET NULL;

-- Lignes de commande : snapshot + détache
ALTER TABLE commande_ligne
    ADD COLUMN piece_nom VARCHAR(120) NULL,
    ADD COLUMN piece_reference VARCHAR(80) NULL;

UPDATE commande_ligne cl
    INNER JOIN device d ON d.id = cl.device_id
SET cl.piece_nom = d.nom,
    cl.piece_reference = d.reference
WHERE cl.piece_nom IS NULL;

ALTER TABLE commande_ligne DROP FOREIGN KEY fk_commande_ligne_device;
ALTER TABLE commande_ligne MODIFY device_id BIGINT NULL;
ALTER TABLE commande_ligne
    ADD CONSTRAINT fk_commande_ligne_device
        FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE SET NULL;

-- Historique prix : purgé explicitement par AdminPurgeService (alerte → observation → device).
