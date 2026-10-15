-- Type de tâche + checklist multi-MAS pour les nettoyages.

ALTER TABLE todo_tache
    ADD COLUMN type_tache VARCHAR(40) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE todo_tache
    ADD CONSTRAINT chk_todo_tache_type CHECK (type_tache IN ('STANDARD', 'NETTOYAGE_MAS'));

CREATE TABLE todo_nettoyage_mas_ligne (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    todo_tache_id           BIGINT       NOT NULL,
    mas_id                  BIGINT       NOT NULL,
    coche                   BOOLEAN      NOT NULL DEFAULT FALSE,
    coche_at                DATETIME(6)  NULL,
    coche_by_username       VARCHAR(120) NULL,
    coche_by_display_name   VARCHAR(160) NULL,
    CONSTRAINT fk_todo_nettoyage_tache FOREIGN KEY (todo_tache_id)
        REFERENCES todo_tache (id) ON DELETE CASCADE,
    CONSTRAINT fk_todo_nettoyage_mas FOREIGN KEY (mas_id)
        REFERENCES mas (id) ON DELETE CASCADE,
    CONSTRAINT uk_todo_nettoyage_tache_mas UNIQUE (todo_tache_id, mas_id)
);

CREATE INDEX idx_todo_nettoyage_mas_coche ON todo_nettoyage_mas_ligne (mas_id, coche_at);
CREATE INDEX idx_todo_nettoyage_tache ON todo_nettoyage_mas_ligne (todo_tache_id);
