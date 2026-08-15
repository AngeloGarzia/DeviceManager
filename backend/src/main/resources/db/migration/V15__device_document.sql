-- Documents PDF attachés aux pièces détachées (manuel, datasheet, notice).
CREATE TABLE device_document (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id     BIGINT       NOT NULL,
    doc_type      VARCHAR(20)  NOT NULL,
    file_key      VARCHAR(512) NOT NULL,
    file_url      VARCHAR(1024) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NULL,
    file_size     BIGINT       NULL,
    CONSTRAINT fk_device_document_device
        FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT ck_device_document_type
        CHECK (doc_type IN ('MANUAL', 'DATASHEET', 'NOTICE')),
    CONSTRAINT uk_device_document_type
        UNIQUE (device_id, doc_type)
);

CREATE INDEX idx_device_document_device ON device_document (device_id);
