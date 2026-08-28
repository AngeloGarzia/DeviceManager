-- Snapshot figé de l'en-tête FIT (valeurs MAS à la création / liaison).
-- Seules date_cessation et destination_machine_usagee restent évolutives.

ALTER TABLE fit
    ADD COLUMN numero_socle VARCHAR(80) NULL AFTER numero_serie_machine,
    ADD COLUMN taux_redistribution DECIMAL(6, 2) NULL AFTER numero_socle,
    ADD COLUMN deno_id BIGINT NULL AFTER taux_redistribution,
    ADD COLUMN multi_deno BOOLEAN NOT NULL DEFAULT FALSE AFTER deno_id,
    ADD COLUMN header_frozen BOOLEAN NOT NULL DEFAULT FALSE AFTER multi_deno,
    ADD CONSTRAINT fk_fit_deno FOREIGN KEY (deno_id) REFERENCES deno (id),
    ADD CONSTRAINT chk_fit_taux
        CHECK (taux_redistribution IS NULL OR (taux_redistribution >= 0 AND taux_redistribution <= 100));

UPDATE fit f
    INNER JOIN mas m ON f.mas_id = m.id
SET f.numero_socle = m.numero_socle,
    f.taux_redistribution = m.taux_redistribution,
    f.deno_id = m.deno_id,
    f.multi_deno = m.multi_deno,
    f.header_frozen = TRUE
WHERE f.mas_id IS NOT NULL;

UPDATE fit f
    INNER JOIN mas m ON f.atelier_id = m.atelier_id
        AND UPPER(f.numero_machine_casino) = UPPER(m.numero)
    LEFT JOIN marque_mas mm ON mm.id = m.marque_id
SET f.mas_id = COALESCE(f.mas_id, m.id),
    f.numero_socle = COALESCE(f.numero_socle, m.numero_socle),
    f.taux_redistribution = COALESCE(f.taux_redistribution, m.taux_redistribution),
    f.deno_id = COALESCE(f.deno_id, m.deno_id),
    f.multi_deno = CASE WHEN f.header_frozen THEN f.multi_deno ELSE m.multi_deno END,
    f.date_mise_en_service = COALESCE(f.date_mise_en_service, m.date_mise_en_service),
    f.marque = COALESCE(f.marque, mm.label),
    f.type_machine = COALESCE(f.type_machine, m.type_machine),
    f.numero_serie_machine = COALESCE(f.numero_serie_machine, m.numero_serie),
    f.header_frozen = TRUE
WHERE f.header_frozen = FALSE;
