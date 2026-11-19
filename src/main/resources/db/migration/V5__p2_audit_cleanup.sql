-- Advance: keep original request reason when rejecting/cancelling.
ALTER TABLE `advances`
  ADD COLUMN `reject_reason` text NULL AFTER `reason`;

-- PWA / web punches (keep MOBILE for legacy rows).
ALTER TABLE `attendance`
  MODIFY COLUMN `source` enum('THUMB','MOBILE','MANUAL','WEB') NOT NULL DEFAULT 'MOBILE';

ALTER TABLE `attendance_punches`
  MODIFY COLUMN `source` enum('MOBILE','THUMB','MANUAL','WEB') NOT NULL DEFAULT 'MOBILE';

-- Persist access-token revocation across JVM restarts.
ALTER TABLE `users`
  ADD COLUMN `tokens_revoked_before` timestamp NULL DEFAULT NULL AFTER `profile_pic_url`;
