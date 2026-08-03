CREATE DATABASE IF NOT EXISTS device_manager
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE device_manager;

CREATE TABLE IF NOT EXISTS groupe (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS casino (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  groupe_id BIGINT NOT NULL,
  CONSTRAINT fk_casino_groupe FOREIGN KEY (groupe_id) REFERENCES groupe(id)
);

CREATE TABLE IF NOT EXISTS atelier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(160) NOT NULL,
  casino_id BIGINT NOT NULL,
  CONSTRAINT fk_atelier_casino FOREIGN KEY (casino_id) REFERENCES casino(id)
);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  nom VARCHAR(80) NOT NULL DEFAULT '',
  prenom VARCHAR(80) NOT NULL DEFAULT '',
  email VARCHAR(160) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  groupe_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_groupe FOREIGN KEY (groupe_id) REFERENCES groupe(id)
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
  CONSTRAINT fk_sfm_contact_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS marque_mas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(60) NOT NULL UNIQUE,
  label VARCHAR(120) NOT NULL UNIQUE
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
  utilise BOOLEAN NOT NULL DEFAULT TRUE,
  atelier_id BIGINT NOT NULL,
  CONSTRAINT fk_mas_marque FOREIGN KEY (marque_id) REFERENCES marque_mas(id),
  CONSTRAINT fk_mas_atelier FOREIGN KEY (atelier_id) REFERENCES atelier(id),
  CONSTRAINT uk_mas_numero_atelier UNIQUE (numero, atelier_id)
);

CREATE TABLE IF NOT EXISTS device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(120) NOT NULL,
  reference VARCHAR(80),
  usage_text VARCHAR(500) NOT NULL,
  date_acquisition DATE NOT NULL,
  obsolete BOOLEAN NOT NULL DEFAULT FALSE,
  photo_key VARCHAR(512),
  photo_url VARCHAR(1024),
  content_type VARCHAR(100),
  file_size BIGINT,
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
  content_type VARCHAR(100),
  file_size BIGINT,
  position INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_device_photo_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS commande (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  technicien_id BIGINT NOT NULL,
  technicien_nom VARCHAR(120) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  date_demande DATETIME NOT NULL,
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
  CONSTRAINT fk_commande_ligne_commande FOREIGN KEY (commande_id) REFERENCES commande(id) ON DELETE CASCADE,
  CONSTRAINT fk_commande_ligne_device FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE TABLE IF NOT EXISTS app_setting (
  setting_key VARCHAR(80) PRIMARY KEY,
  setting_value VARCHAR(1000),
  label VARCHAR(160) NOT NULL,
  category VARCHAR(40) NOT NULL,
  secret_value BOOLEAN NOT NULL DEFAULT FALSE
);
