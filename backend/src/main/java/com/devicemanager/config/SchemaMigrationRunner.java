package com.devicemanager.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migrations schéma légères (commande multi-lignes, marques MAS en table).
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        softenCommandeLegacyColumns();
        migrateMasMarqueToTable();
        migrateDeviceMarqueInheritance();
        migrateSfmMarques();
        migrateDevicePhotos();
        repairOrphanAtelierAndMarqueIds();
        softenDeviceOptionalColumns();
    }

    private void softenCommandeLegacyColumns() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'commande'
                      AND COLUMN_NAME = 'device_id'
                    """,
                    Integer.class);
            if (count == null || count == 0) {
                return;
            }
            try {
                jdbcTemplate.execute("ALTER TABLE commande DROP FOREIGN KEY fk_commande_device");
            } catch (Exception ignored) {
                // contrainte déjà absente ou nom différent
            }
            jdbcTemplate.execute("ALTER TABLE commande MODIFY COLUMN device_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE commande MODIFY COLUMN quantite INT NULL");
            log.info("Migration commande: colonnes device_id/quantite rendues optionnelles");
        } catch (Exception ex) {
            log.debug("Migration commande ignorée: {}", ex.getMessage());
        }
    }

    private void migrateMasMarqueToTable() {
        try {
            Integer masTable = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mas'
                    """,
                    Integer.class);
            if (masTable == null || masTable == 0) {
                return;
            }

            seedMarque("ARISTOCRAT", "Aristocrat");
            seedMarque("IGT", "IGT");
            seedMarque("NOVOMATIC", "Novomatic");
            seedMarque("SCIENTIFIC_GAMES", "Scientific Games");
            seedMarque("KONAMI", "Konami");
            seedMarque("AINSWORTH", "Ainsworth");
            seedMarque("BALLY", "Bally");
            seedMarque("WMS", "WMS");
            seedMarque("EVERI", "Everi");
            seedMarque("AMATIC", "Amatic");
            seedMarque("MERKUR", "Merkur");
            seedMarque("AUTRES", "Autres");

            Integer hasMarqueId = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mas'
                      AND COLUMN_NAME = 'marque_id'
                    """,
                    Integer.class);
            if (hasMarqueId == null || hasMarqueId == 0) {
                return;
            }

            Integer hasLegacyMarque = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mas'
                      AND COLUMN_NAME = 'marque'
                      AND DATA_TYPE IN ('varchar', 'char', 'enum', 'text')
                    """,
                    Integer.class);

            if (hasLegacyMarque != null && hasLegacyMarque > 0) {
                jdbcTemplate.update("""
                        UPDATE mas m
                        JOIN marque_mas mm ON mm.code = m.marque
                        SET m.marque_id = mm.id
                        WHERE m.marque_id IS NULL
                        """);
                jdbcTemplate.update("""
                        UPDATE mas m
                        JOIN marque_mas mm ON LOWER(mm.label) = LOWER(m.marque)
                        SET m.marque_id = mm.id
                        WHERE m.marque_id IS NULL
                        """);
            }

            Long autresId = jdbcTemplate.query(
                    "SELECT id FROM marque_mas WHERE code = 'AUTRES' LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null);
            if (autresId != null) {
                jdbcTemplate.update("UPDATE mas SET marque_id = ? WHERE marque_id IS NULL", autresId);
            }

            try {
                jdbcTemplate.execute("ALTER TABLE mas MODIFY COLUMN marque_id BIGINT NOT NULL");
            } catch (Exception ignored) {
                // déjà NOT NULL ou données inconsistantes
            }

            log.info("Migration MAS: marques liées à la table marque_mas");
        } catch (Exception ex) {
            log.warn("Migration marques MAS: {}", ex.getMessage());
        }
    }

    private void seedMarque(String code, String label) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM marque_mas WHERE code = ?",
                Integer.class,
                code);
        if (exists != null && exists > 0) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO marque_mas (code, label) VALUES (?, ?)", code, label);
        } catch (Exception ignored) {
            // table pas encore créée
        }
    }

    /**
     * Lie device.marque_id au catalogue partagé marque_mas (héritage depuis la MAS).
     */
    private void migrateDeviceMarqueInheritance() {
        try {
            Integer deviceTable = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device'
                    """,
                    Integer.class);
            if (deviceTable == null || deviceTable == 0) {
                return;
            }

            Integer hasMarqueId = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'device'
                      AND COLUMN_NAME = 'marque_id'
                    """,
                    Integer.class);
            if (hasMarqueId == null || hasMarqueId == 0) {
                jdbcTemplate.execute("ALTER TABLE device ADD COLUMN marque_id BIGINT NULL");
            }

            jdbcTemplate.update("""
                    UPDATE device d
                    JOIN mas m ON m.id = d.mas_id
                    SET d.marque_id = m.marque_id
                    WHERE d.marque_id IS NULL AND m.marque_id IS NOT NULL
                    """);

            Integer nulls = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM device WHERE marque_id IS NULL",
                    Integer.class);
            if (nulls != null && nulls == 0) {
                try {
                    jdbcTemplate.execute("ALTER TABLE device MODIFY COLUMN marque_id BIGINT NOT NULL");
                } catch (Exception ignored) {
                    // déjà NOT NULL
                }
                try {
                    jdbcTemplate.execute("""
                            ALTER TABLE device
                            ADD CONSTRAINT fk_device_marque
                            FOREIGN KEY (marque_id) REFERENCES marque_mas(id)
                            """);
                } catch (Exception ignored) {
                    // contrainte déjà présente
                }
            }

            log.info("Migration device: marque héritée depuis MAS (catalogue marque_mas)");
        } catch (Exception ex) {
            log.warn("Migration marque device: {}", ex.getMessage());
        }
    }

    /**
     * Table de jointure SFM ↔ marques (catalogue partagé), backfill depuis les pièces existantes.
     */
    private void migrateSfmMarques() {
        try {
            Integer sfmTable = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sfm'
                    """,
                    Integer.class);
            if (sfmTable == null || sfmTable == 0) {
                return;
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sfm_marque (
                      sfm_id BIGINT NOT NULL,
                      marque_id BIGINT NOT NULL,
                      PRIMARY KEY (sfm_id, marque_id),
                      CONSTRAINT fk_sfm_marque_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id) ON DELETE CASCADE,
                      CONSTRAINT fk_sfm_marque_marque FOREIGN KEY (marque_id) REFERENCES marque_mas(id)
                    )
                    """);

            Integer deviceHasMarque = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'device'
                      AND COLUMN_NAME = 'marque_id'
                    """,
                    Integer.class);
            if (deviceHasMarque != null && deviceHasMarque > 0) {
                jdbcTemplate.update("""
                        INSERT IGNORE INTO sfm_marque (sfm_id, marque_id)
                        SELECT DISTINCT d.sfm_id, d.marque_id
                        FROM device d
                        WHERE d.sfm_id IS NOT NULL AND d.marque_id IS NOT NULL
                        """);
            }

            log.info("Migration SFM: liaison multi-marques (sfm_marque)");
        } catch (Exception ex) {
            log.warn("Migration sfm_marque: {}", ex.getMessage());
        }
    }

    /**
     * Table device_photo (max 5 images / pièce) + backfill depuis la photo principale legacy.
     */
    private void migrateDevicePhotos() {
        try {
            Integer deviceTable = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device'
                    """,
                    Integer.class);
            if (deviceTable == null || deviceTable == 0) {
                return;
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS device_photo (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      device_id BIGINT NOT NULL,
                      photo_key VARCHAR(512) NOT NULL,
                      photo_url VARCHAR(1024) NOT NULL,
                      content_type VARCHAR(100),
                      file_size BIGINT,
                      position INT NOT NULL DEFAULT 0,
                      CONSTRAINT fk_device_photo_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
                    )
                    """);

            jdbcTemplate.update("""
                    INSERT INTO device_photo (device_id, photo_key, photo_url, content_type, file_size, position)
                    SELECT d.id, d.photo_key, d.photo_url, d.content_type, d.file_size, 0
                    FROM device d
                    WHERE d.photo_key IS NOT NULL
                      AND d.photo_url IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM device_photo p WHERE p.device_id = d.id
                      )
                    """);

            log.info("Migration device: table device_photo (multi-images)");
        } catch (Exception ex) {
            log.warn("Migration device_photo: {}", ex.getMessage());
        }
    }

    /**
     * Répare les lignes legacy avec atelier_id/marque_id = 0 (ou orphelins),
     * qui cassent les FK Hibernate et excluent les pièces de tous les ateliers.
     */
    private void repairOrphanAtelierAndMarqueIds() {
        try {
            Long fallbackAtelier = jdbcTemplate.query(
                    "SELECT id FROM atelier ORDER BY id LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null);
            Long fallbackMarque = jdbcTemplate.query(
                    "SELECT id FROM marque_mas ORDER BY id LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null);
            if (fallbackAtelier == null || fallbackMarque == null) {
                return;
            }

            int masAtelier = jdbcTemplate.update("""
                    UPDATE mas
                    SET atelier_id = ?
                    WHERE atelier_id = 0
                       OR atelier_id NOT IN (SELECT id FROM (SELECT id FROM atelier) a)
                    """, fallbackAtelier);
            int masMarque = jdbcTemplate.update("""
                    UPDATE mas
                    SET marque_id = ?
                    WHERE marque_id = 0
                       OR marque_id NOT IN (SELECT id FROM (SELECT id FROM marque_mas) m)
                    """, fallbackMarque);
            int sfmAtelier = jdbcTemplate.update("""
                    UPDATE sfm
                    SET atelier_id = ?
                    WHERE atelier_id = 0
                       OR atelier_id NOT IN (SELECT id FROM (SELECT id FROM atelier) a)
                    """, fallbackAtelier);
            int deviceAtelier = jdbcTemplate.update("""
                    UPDATE device d
                    JOIN mas m ON m.id = d.mas_id
                    SET d.atelier_id = m.atelier_id
                    WHERE d.atelier_id = 0
                       OR d.atelier_id NOT IN (SELECT id FROM (SELECT id FROM atelier) a)
                    """);
            int deviceMarque = jdbcTemplate.update("""
                    UPDATE device d
                    JOIN mas m ON m.id = d.mas_id
                    SET d.marque_id = m.marque_id
                    WHERE d.marque_id = 0
                       OR d.marque_id NOT IN (SELECT id FROM (SELECT id FROM marque_mas) mm)
                    """);

            if (masAtelier + masMarque + sfmAtelier + deviceAtelier + deviceMarque > 0) {
                log.info(
                        "Migration réparation orphelins: mas(atelier={}, marque={}), sfm(atelier={}), device(atelier={}, marque={})",
                        masAtelier, masMarque, sfmAtelier, deviceAtelier, deviceMarque);
            }
        } catch (Exception ex) {
            log.warn("Migration réparation orphelins: {}", ex.getMessage());
        }
    }

    /**
     * Référence, MAS et marque de pièce optionnelles.
     */
    private void softenDeviceOptionalColumns() {
        try {
            Integer deviceTable = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device'
                    """,
                    Integer.class);
            if (deviceTable == null || deviceTable == 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE device MODIFY COLUMN reference VARCHAR(80) NULL");
            jdbcTemplate.execute("ALTER TABLE device MODIFY COLUMN sfm_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE device MODIFY COLUMN mas_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE device MODIFY COLUMN marque_id BIGINT NULL");
            log.info("Migration device: reference / sfm_id / mas_id / marque_id rendus optionnels");
        } catch (Exception ex) {
            log.warn("Migration device champs optionnels: {}", ex.getMessage());
        }
    }
}
