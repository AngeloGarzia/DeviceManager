-- Idempotence des e-mails de rappels planifiés (1 envoi / job / jour / destinataire).
CREATE TABLE scheduled_mail_send (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_key       VARCHAR(64)  NOT NULL,
    period_key    VARCHAR(32)  NOT NULL,
    recipient     VARCHAR(255) NOT NULL,
    sent_at       DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_scheduled_mail_send (job_key, period_key, recipient)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
