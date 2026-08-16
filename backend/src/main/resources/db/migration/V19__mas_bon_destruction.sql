-- Bon de destruction (PDF ou image) associé à une MAS détruite.
ALTER TABLE mas
    ADD COLUMN destruction_file_key      VARCHAR(512)  NULL,
    ADD COLUMN destruction_file_url      VARCHAR(1024) NULL,
    ADD COLUMN destruction_original_name VARCHAR(255)  NULL,
    ADD COLUMN destruction_content_type  VARCHAR(120)  NULL,
    ADD COLUMN destruction_file_size     BIGINT        NULL,
    ADD COLUMN destruction_uploaded_at   DATETIME      NULL;
