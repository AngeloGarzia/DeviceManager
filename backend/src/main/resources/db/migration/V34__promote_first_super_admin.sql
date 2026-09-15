-- Promeut le premier administrateur en SUPER_ADMIN s'il n'en existe encore aucun.
-- Variables : évite l'erreur MySQL 1093 (UPDATE users ... FROM users).
SET @has_super_admin := (SELECT COUNT(*) FROM users WHERE role = 'SUPER_ADMIN');
SET @first_admin_id := (
    SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id ASC LIMIT 1
);

UPDATE users
SET role = 'SUPER_ADMIN'
WHERE id = @first_admin_id
  AND @has_super_admin = 0
  AND @first_admin_id IS NOT NULL;
