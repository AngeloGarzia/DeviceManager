-- Identification machine sur MAS (aligné modèle FIT / fiche inventaire).
ALTER TABLE mas
    ADD COLUMN date_mise_en_service         DATE         NULL,
    ADD COLUMN type_machine                 VARCHAR(120) NULL,
    ADD COLUMN numero_serie                 VARCHAR(120) NULL,
    ADD COLUMN date_cessation               DATE         NULL,
    ADD COLUMN destination_machine_usagee   VARCHAR(255) NULL;
