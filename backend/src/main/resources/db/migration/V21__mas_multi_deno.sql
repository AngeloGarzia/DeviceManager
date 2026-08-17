-- MAS multi-dénomination : flag indépendant de la dénomination numérique du référentiel.
ALTER TABLE mas
    ADD COLUMN multi_deno BOOLEAN NOT NULL DEFAULT FALSE;
