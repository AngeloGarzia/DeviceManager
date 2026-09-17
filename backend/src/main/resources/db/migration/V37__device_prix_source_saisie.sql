-- Autorise les observations de prix saisies à la création de pièce (hors devis).
ALTER TABLE device_prix_observation DROP CHECK chk_prix_obs_source;
ALTER TABLE device_prix_observation
    ADD CONSTRAINT chk_prix_obs_source CHECK (source IN ('DEVIS', 'SAISIE'));
