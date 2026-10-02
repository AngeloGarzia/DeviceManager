-- Photos optionnelles (0 à 5) attachées à une tâche simple.

CREATE TABLE todo_tache_photo (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    todo_tache_id   BIGINT       NOT NULL,
    photo_key       VARCHAR(512) NOT NULL,
    photo_url       VARCHAR(1024) NOT NULL,
    content_type    VARCHAR(100) NULL,
    file_size       BIGINT       NULL,
    position        INT          NOT NULL,
    CONSTRAINT fk_todo_tache_photo_todo FOREIGN KEY (todo_tache_id)
        REFERENCES todo_tache (id) ON DELETE CASCADE
);

CREATE INDEX idx_todo_tache_photo_todo ON todo_tache_photo (todo_tache_id, position);
