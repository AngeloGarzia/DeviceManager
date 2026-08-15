-- Statut d'exploitation MAS : Utilisée | En réserve | Vendue | Détruite.

ALTER TABLE mas
    ADD COLUMN statut VARCHAR(40) NOT NULL DEFAULT 'UTILISEE';

UPDATE mas
SET statut = CASE
    WHEN utilise = 1 OR utilise = TRUE THEN 'UTILISEE'
    ELSE 'EN_RESERVE'
END;

CREATE INDEX idx_mas_statut ON mas (atelier_id, statut);
