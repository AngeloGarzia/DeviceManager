-- Casino utilisable : si false, ateliers enfants désactivés et alertes/mails exclus.
ALTER TABLE casino
    ADD COLUMN utilise BOOLEAN NOT NULL DEFAULT TRUE;
