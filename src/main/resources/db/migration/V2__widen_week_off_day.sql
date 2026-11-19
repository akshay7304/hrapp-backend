-- Multi week-off support stores CSV day names (e.g. SATURDAY,SUNDAY).
-- Baseline had varchar(10), which only fits a single day name.
ALTER TABLE `company_settings`
  MODIFY COLUMN `week_off_day` varchar(100) NOT NULL DEFAULT 'SUNDAY';
