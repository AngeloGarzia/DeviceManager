-- Clôture signée des tâches « À faire ».

ALTER TABLE todo_tache
    ADD COLUMN completed_by_display_name VARCHAR(160) NULL AFTER completed_by_username,
    ADD COLUMN signature_cloture LONGTEXT NULL AFTER completed_by_display_name,
    ADD COLUMN signataire_cloture_nom VARCHAR(120) NULL AFTER signature_cloture;
