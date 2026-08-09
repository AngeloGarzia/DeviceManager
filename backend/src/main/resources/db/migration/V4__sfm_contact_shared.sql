-- Contacts SFM partageables (N–N) + flag Technicien SFM.

ALTER TABLE sfm_contact
  ADD COLUMN technicien_sfm TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS sfm_sfm_contact (
  sfm_id BIGINT NOT NULL,
  contact_id BIGINT NOT NULL,
  position INT NOT NULL DEFAULT 0,
  PRIMARY KEY (sfm_id, contact_id),
  CONSTRAINT fk_sfm_sfm_contact_sfm FOREIGN KEY (sfm_id) REFERENCES sfm(id) ON DELETE CASCADE,
  CONSTRAINT fk_sfm_sfm_contact_contact FOREIGN KEY (contact_id) REFERENCES sfm_contact(id) ON DELETE CASCADE
);

INSERT INTO sfm_sfm_contact (sfm_id, contact_id, position)
SELECT c.sfm_id, c.id, 0
FROM sfm_contact c
WHERE c.sfm_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sfm_sfm_contact l
    WHERE l.sfm_id = c.sfm_id AND l.contact_id = c.id
  );

ALTER TABLE sfm_contact DROP FOREIGN KEY fk_sfm_contact_sfm;
ALTER TABLE sfm_contact DROP COLUMN sfm_id;
