-- Règles de tâches récurrentes + extensions todo_tache (occurrences).

CREATE TABLE todo_recurrence (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id              BIGINT       NOT NULL,
    titre                   VARCHAR(200) NOT NULL,
    description             VARCHAR(2000) NULL,
    severite                VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    mas_id                  BIGINT       NULL,
    frequence               VARCHAR(20)  NOT NULL,
    interval_days           INT          NULL,
    jour_semaine            TINYINT      NULL,
    jour_mois               TINYINT      NULL,
    heure_due               TIME         NOT NULL DEFAULT '08:00:00',
    date_debut              DATE         NOT NULL,
    date_fin                DATE         NULL,
    active                  TINYINT(1)   NOT NULL DEFAULT 1,
    prochaine_echeance      DATETIME(6)  NULL,
    created_by_username     VARCHAR(120) NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    CONSTRAINT fk_todo_recurrence_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_todo_recurrence_mas FOREIGN KEY (mas_id) REFERENCES mas (id) ON DELETE SET NULL,
    CONSTRAINT chk_todo_recurrence_severite CHECK (severite IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_todo_recurrence_frequence CHECK (
        frequence IN ('DAILY', 'WEEKLY', 'MONTHLY', 'INTERVAL_DAYS')
    )
);

CREATE INDEX idx_todo_recurrence_atelier_active ON todo_recurrence (atelier_id, active);
CREATE INDEX idx_todo_recurrence_active_next ON todo_recurrence (active, prochaine_echeance);

ALTER TABLE todo_tache
    ADD COLUMN recurrence_id BIGINT NULL,
    ADD COLUMN due_at DATETIME(6) NULL,
    ADD COLUMN occurrence_key VARCHAR(32) NULL,
    ADD CONSTRAINT fk_todo_tache_recurrence
        FOREIGN KEY (recurrence_id) REFERENCES todo_recurrence (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_todo_tache_recurrence_occurrence
    ON todo_tache (recurrence_id, occurrence_key);

CREATE INDEX idx_todo_tache_atelier_statut_due
    ON todo_tache (atelier_id, statut, due_at);
