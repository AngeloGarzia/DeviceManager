-- Préférence par compte : recevoir les e-mails d'alerte planifiés (ADMIN / SUPER_ADMIN).
ALTER TABLE users
    ADD COLUMN receive_alert_mails TINYINT(1) NOT NULL DEFAULT 1;
