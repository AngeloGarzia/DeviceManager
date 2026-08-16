-- Devis PDF associé à une commande après validation admin.
ALTER TABLE commande
    ADD COLUMN devis_file_key      VARCHAR(512)  NULL,
    ADD COLUMN devis_file_url      VARCHAR(1024) NULL,
    ADD COLUMN devis_original_name VARCHAR(255)  NULL,
    ADD COLUMN devis_content_type  VARCHAR(120)  NULL,
    ADD COLUMN devis_file_size     BIGINT        NULL,
    ADD COLUMN devis_uploaded_at   DATETIME      NULL;
