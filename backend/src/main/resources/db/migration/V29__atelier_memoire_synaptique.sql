-- Mémoire synaptique IA : situation persistante par atelier (1 ligne / atelier).
CREATE TABLE atelier_memoire_synaptique (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    atelier_id      BIGINT       NOT NULL,
    overview        MEDIUMTEXT   NOT NULL,
    facts_json      MEDIUMTEXT   NULL,
    last_event_type VARCHAR(80)  NULL,
    last_event_at   DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_atelier_memoire_synaptique_atelier UNIQUE (atelier_id),
    CONSTRAINT fk_atelier_memoire_synaptique_atelier
        FOREIGN KEY (atelier_id) REFERENCES atelier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
