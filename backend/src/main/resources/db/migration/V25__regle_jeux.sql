-- Catalogue global des règles de jeux (PDF) et liaison M:N avec les MAS.

CREATE TABLE regle_jeux (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    label VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    file_key VARCHAR(512) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT,
    uploaded_at DATETIME(6),
    CONSTRAINT uk_regle_jeux_code UNIQUE (code),
    CONSTRAINT uk_regle_jeux_label UNIQUE (label)
);

CREATE TABLE mas_regle_jeux (
    mas_id BIGINT NOT NULL,
    regle_jeux_id BIGINT NOT NULL,
    PRIMARY KEY (mas_id, regle_jeux_id),
    CONSTRAINT fk_mas_regle_jeux_mas FOREIGN KEY (mas_id) REFERENCES mas (id) ON DELETE CASCADE,
    CONSTRAINT fk_mas_regle_jeux_regle FOREIGN KEY (regle_jeux_id) REFERENCES regle_jeux (id) ON DELETE RESTRICT
);

CREATE INDEX idx_mas_regle_jeux_regle ON mas_regle_jeux (regle_jeux_id);
