-- Flyway V1: baseline schema matching current JPA entities (MySQL 8).
-- CREATE TABLE IF NOT EXISTS so the migration is safe on existing databases.

CREATE TABLE IF NOT EXISTS groupe (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  CONSTRAINT uk_groupe_nom UNIQUE (nom)
);

CREATE TABLE IF NOT EXISTS casino (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  groupe_id BIGINT NOT NULL,
  CONSTRAINT fk_casino_groupe FOREIGN KEY (groupe_id) REFERENCES groupe(id)
);

CREATE TABLE IF NOT EXISTS coordonnees (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adresse_ligne1 VARCHAR(160) NULL,
  adresse_ligne2 VARCHAR(160) NULL,
  adresse_code_postal VARCHAR(20) NULL,
  adresse_ville VARCHAR(120) NULL,
  adresse_pays VARCHAR(80) NULL
);

CREATE TABLE IF NOT EXISTS atelier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(160) NOT NULL,
  casino_id BIGINT NOT NULL,
  coordonnees_id BIGINT NULL,
  CONSTRAINT uk_atelier_coordonnees UNIQUE (coordonnees_id),
  CONSTRAINT fk_atelier_casino FOREIGN KEY (casino_id) REFERENCES casino(id),
  CONSTRAINT fk_atelier_coordonnees FOREIGN KEY (coordonnees_id) REFERENCES coordonnees(id)
);

CREATE TABLE IF NOT EXISTS email_coord (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coordonnees_id BIGINT NOT NULL,
  valeur VARCHAR(160) NOT NULL,
  principal TINYINT(1) NOT NULL,
  CONSTRAINT fk_email_coord_coordonnees FOREIGN KEY (coordonnees_id) REFERENCES coordonnees(id)
);

CREATE TABLE IF NOT EXISTS telephone_coord (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coordonnees_id BIGINT NOT NULL,
  valeur VARCHAR(40) NOT NULL,
  label VARCHAR(40) NULL,
  principal TINYINT(1) NOT NULL,
  CONSTRAINT fk_telephone_coord_coordonnees FOREIGN KEY (coordonnees_id) REFERENCES coordonnees(id)
);

CREATE TABLE IF NOT EXISTS reseau_social (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coordonnees_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  url VARCHAR(255) NOT NULL,
  CONSTRAINT fk_reseau_social_coordonnees FOREIGN KEY (coordonnees_id) REFERENCES coordonnees(id)
);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  nom VARCHAR(80) NOT NULL,
  prenom VARCHAR(80) NOT NULL,
  email VARCHAR(160) NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  groupe_id BIGINT NULL,
  preferred_atelier_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_users_username UNIQUE (username),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT fk_user_groupe FOREIGN KEY (groupe_id) REFERENCES groupe(id),
  CONSTRAINT fk_user_preferred_atelier FOREIGN KEY (preferred_atelier_id) REFERENCES atelier(id)
);

CREATE TABLE IF NOT EXISTS atelier_responsable (
  atelier_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (atelier_id, user_id),
  CONSTRAINT fk_atelier_resp_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id),
  CONSTRAINT fk_atelier_resp_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS marque_mas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(60) NOT NULL,
  label VARCHAR(120) NOT NULL,
  CONSTRAINT uk_marque_mas_code UNIQUE (code),
  CONSTRAINT uk_marque_mas_label UNIQUE (label)
);

CREATE TABLE IF NOT EXISTS sfm (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  responsable VARCHAR(120) NOT NULL,
  telephone VARCHAR(40) NOT NULL,
  email VARCHAR(160) NOT NULL,
  atelier_id BIGINT NOT NULL,
  CONSTRAINT fk_sfm_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id),
  CONSTRAINT uk_sfm_nom_atelier UNIQUE (nom, atelier_id)
);

CREATE TABLE IF NOT EXISTS sfm_contact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sfm_id BIGINT NOT NULL,
  nom VARCHAR(120) NOT NULL,
  telephone VARCHAR(40) NOT NULL,
  email VARCHAR(160) NOT NULL,
  receive_order_mails TINYINT(1) NULL,
  CONSTRAINT fk_sfm_contact_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id)
);

CREATE TABLE IF NOT EXISTS sfm_marque (
  sfm_id BIGINT NOT NULL,
  marque_id BIGINT NOT NULL,
  PRIMARY KEY (sfm_id, marque_id),
  CONSTRAINT fk_sfm_marque_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id) ON DELETE CASCADE,
  CONSTRAINT fk_sfm_marque_marque FOREIGN KEY (marque_id) REFERENCES marque_mas(id)
);

CREATE TABLE IF NOT EXISTS mas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  numero VARCHAR(80) NOT NULL,
  marque_id BIGINT NOT NULL,
  utilise TINYINT(1) NOT NULL,
  atelier_id BIGINT NOT NULL,
  CONSTRAINT fk_mas_marque FOREIGN KEY (marque_id) REFERENCES marque_mas(id),
  CONSTRAINT fk_mas_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id),
  CONSTRAINT uk_mas_numero_atelier UNIQUE (numero, atelier_id)
);

CREATE TABLE IF NOT EXISTS device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  reference VARCHAR(80) NULL,
  usage_text VARCHAR(500) NOT NULL,
  date_acquisition DATE NOT NULL,
  obsolete TINYINT(1) NOT NULL,
  photo_key VARCHAR(512) NULL,
  photo_url VARCHAR(1024) NULL,
  content_type VARCHAR(100) NULL,
  file_size BIGINT NULL,
  sfm_id BIGINT NULL,
  mas_id BIGINT NULL,
  marque_id BIGINT NULL,
  atelier_id BIGINT NOT NULL,
  CONSTRAINT fk_device_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id),
  CONSTRAINT fk_device_mas FOREIGN KEY (mas_id) REFERENCES mas(id),
  CONSTRAINT fk_device_marque FOREIGN KEY (marque_id) REFERENCES marque_mas(id),
  CONSTRAINT fk_device_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id),
  CONSTRAINT uk_device_nom_atelier UNIQUE (nom, atelier_id),
  CONSTRAINT uk_device_reference_atelier UNIQUE (reference, atelier_id)
);

CREATE TABLE IF NOT EXISTS device_photo (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id BIGINT NOT NULL,
  photo_key VARCHAR(512) NOT NULL,
  photo_url VARCHAR(1024) NOT NULL,
  content_type VARCHAR(100) NULL,
  file_size BIGINT NULL,
  position INT NOT NULL,
  CONSTRAINT fk_device_photo_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS commande (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  technicien_id BIGINT NOT NULL,
  technicien_nom VARCHAR(120) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  date_demande DATETIME(6) NOT NULL,
  status VARCHAR(30) NOT NULL,
  atelier_id BIGINT NOT NULL,
  CONSTRAINT fk_commande_technicien FOREIGN KEY (technicien_id) REFERENCES users(id),
  CONSTRAINT fk_commande_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id)
);

CREATE TABLE IF NOT EXISTS commande_ligne (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  commande_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  quantite INT NOT NULL,
  CONSTRAINT fk_commande_ligne_commande FOREIGN KEY (commande_id) REFERENCES commande(id),
  CONSTRAINT fk_commande_ligne_device FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE TABLE IF NOT EXISTS app_setting (
  setting_key VARCHAR(80) NOT NULL PRIMARY KEY,
  setting_value VARCHAR(1000) NULL,
  label VARCHAR(160) NOT NULL,
  category VARCHAR(40) NOT NULL,
  secret_value TINYINT(1) NOT NULL
);

CREATE TABLE IF NOT EXISTS upload_blob (
  object_key VARCHAR(512) NOT NULL PRIMARY KEY,
  data LONGBLOB NOT NULL,
  content_type VARCHAR(100) NULL,
  file_size BIGINT NULL
);

CREATE TABLE IF NOT EXISTS app_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  level VARCHAR(16) NOT NULL,
  logger_name VARCHAR(255) NOT NULL,
  thread_name VARCHAR(120) NULL,
  message TEXT NOT NULL,
  throwable MEDIUMTEXT NULL,
  INDEX idx_app_log_created (created_at),
  INDEX idx_app_log_level (level)
);
