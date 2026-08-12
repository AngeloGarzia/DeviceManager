-- Timeline : horodatage validation/réception + journal des mouvements de stock.
ALTER TABLE commande
    ADD COLUMN date_validation DATETIME NULL,
    ADD COLUMN date_reception DATETIME NULL;

CREATE TABLE stock_mouvement (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id       BIGINT       NOT NULL,
    device_id        BIGINT       NOT NULL,
    piece_nom        VARCHAR(120) NOT NULL,
    piece_reference  VARCHAR(80)  NULL,
    delta            INT          NOT NULL,
    stock_avant      INT          NOT NULL,
    stock_apres      INT          NOT NULL,
    source_type      VARCHAR(30)  NOT NULL,
    source_id        BIGINT       NULL,
    acteur_nom       VARCHAR(120) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_mouvement_atelier
        FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_stock_mouvement_device
        FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT chk_stock_mouvement_source_type
        CHECK (source_type IN ('INTERVENTION', 'ORDER_RECEIVE', 'MANUAL'))
);

CREATE INDEX idx_stock_mouvement_atelier_created
    ON stock_mouvement (atelier_id, created_at DESC);
