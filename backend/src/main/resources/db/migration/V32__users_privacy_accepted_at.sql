ALTER TABLE users ADD COLUMN privacy_accepted_at TIMESTAMP(6) NULL;

-- Comptes déjà existants : ne pas bloquer la prod à la prochaine connexion.
UPDATE users SET privacy_accepted_at = CURRENT_TIMESTAMP(6) WHERE privacy_accepted_at IS NULL;
