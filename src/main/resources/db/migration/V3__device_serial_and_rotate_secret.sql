-- Device serial for biometric company routing (SN / deviceId from the reader).
-- Employee enrol ids stay on users.thumb_device_id — do not reuse that column.
ALTER TABLE `company_settings`
  ADD COLUMN `device_serial` varchar(100) NULL AFTER `device_secret`;

ALTER TABLE `company_settings`
  ADD UNIQUE KEY `uq_settings_device_serial` (`device_serial`);

-- Rotate the well-known default secret so public /thumb/* cannot use a shared guess.
UPDATE `company_settings`
SET `device_secret` = REPLACE(UUID(), '-', '')
WHERE `device_secret` = 'hrapp-device-secret-2026';
