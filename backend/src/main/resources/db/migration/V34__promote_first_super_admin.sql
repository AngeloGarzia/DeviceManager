-- Promeut le premier administrateur en SUPER_ADMIN s'il n'en existe encore aucun.
UPDATE users
SET role = 'SUPER_ADMIN'
WHERE id = (
    SELECT id FROM (
        SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id ASC LIMIT 1
    ) AS first_admin
)
AND NOT EXISTS (SELECT 1 FROM users WHERE role = 'SUPER_ADMIN');
