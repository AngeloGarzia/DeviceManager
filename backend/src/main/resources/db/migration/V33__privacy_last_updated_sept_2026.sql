-- Met à jour la date affichée sur la page confidentialité après révision du texte RGPD.
UPDATE app_setting
SET setting_value = '15 septembre 2026'
WHERE setting_key = 'PRIVACY_LAST_UPDATED';
