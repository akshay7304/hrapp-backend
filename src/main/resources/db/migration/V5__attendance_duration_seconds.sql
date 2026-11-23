-- Attendance durations were NUMERIC(4,2) hours, which drops seconds
-- (08:29:57 became 8.50). worked_seconds / overtime_seconds are the exact
-- duration. The decimal columns stay as a payroll snapshot only.

ALTER TABLE attendance
    ADD COLUMN worked_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN overtime_seconds BIGINT NOT NULL DEFAULT 0;

UPDATE attendance
SET worked_seconds = CAST(ROUND(worked_hours * 3600) AS BIGINT),
    overtime_seconds = CAST(ROUND(overtime_hours * 3600) AS BIGINT);
