-- Atelier utilisable / proposé aux utilisateurs (sinon visible seulement en Setup).
ALTER TABLE atelier
    ADD COLUMN utilise BOOLEAN NOT NULL DEFAULT TRUE;
