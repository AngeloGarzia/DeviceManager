-- Tâches « À faire » persistées par atelier (cycle de vie + lien intervention).

CREATE TABLE todo_tache (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id                  BIGINT       NOT NULL,
    titre                       VARCHAR(200) NOT NULL,
    description                 VARCHAR(2000) NULL,
    statut                      VARCHAR(40)  NOT NULL,
    severite                    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    created_by_username         VARCHAR(120) NOT NULL,
    created_by_display_name     VARCHAR(160) NULL,
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    completed_at                DATETIME(6)  NULL,
    completed_by_username       VARCHAR(120) NULL,
    mas_id                      BIGINT       NULL,
    intervention_technique_id   BIGINT       NULL,
    intervention_id             BIGINT       NULL,
    CONSTRAINT fk_todo_tache_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_todo_tache_mas FOREIGN KEY (mas_id) REFERENCES mas (id) ON DELETE SET NULL,
    CONSTRAINT fk_todo_tache_it FOREIGN KEY (intervention_technique_id)
        REFERENCES interventions (id) ON DELETE SET NULL,
    CONSTRAINT fk_todo_tache_bi FOREIGN KEY (intervention_id)
        REFERENCES intervention (id) ON DELETE SET NULL,
    CONSTRAINT chk_todo_tache_statut CHECK (statut IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT chk_todo_tache_severite CHECK (severite IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_todo_tache_atelier_statut ON todo_tache (atelier_id, statut, created_at);
CREATE INDEX idx_todo_tache_mas ON todo_tache (mas_id);
