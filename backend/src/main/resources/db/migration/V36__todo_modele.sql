-- Modèles de tâches simples (création rapide par les techniciens).

CREATE TABLE todo_modele (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id          BIGINT       NOT NULL,
    titre               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000) NULL,
    severite            VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    mas_id              BIGINT       NULL,
    position            INT          NOT NULL DEFAULT 0,
    created_by_username VARCHAR(120) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT fk_todo_modele_atelier FOREIGN KEY (atelier_id) REFERENCES atelier (id),
    CONSTRAINT fk_todo_modele_mas FOREIGN KEY (mas_id) REFERENCES mas (id) ON DELETE SET NULL,
    CONSTRAINT chk_todo_modele_severite CHECK (severite IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_todo_modele_atelier_position ON todo_modele (atelier_id, position, titre);
