-- Les prompts IA (Setup) dépassent largement VARCHAR(1000).
ALTER TABLE app_setting
  MODIFY COLUMN setting_value TEXT NULL;
