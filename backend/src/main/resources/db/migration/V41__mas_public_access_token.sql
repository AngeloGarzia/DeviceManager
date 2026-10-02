-- Token d'accès public opaque pour consultation des règles de jeux (QR code).

ALTER TABLE mas
    ADD COLUMN public_access_token VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_mas_public_access_token ON mas (public_access_token);
