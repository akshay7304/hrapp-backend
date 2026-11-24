-- Persist late flag at write time so history/list reads never re-resolve shifts.
ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS is_late BOOLEAN NULL;
