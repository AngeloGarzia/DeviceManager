-- Commentaire de clôture des tâches « À faire ».

ALTER TABLE todo_tache
    ADD COLUMN commentaire_cloture VARCHAR(2000) NULL AFTER signataire_cloture_nom;
