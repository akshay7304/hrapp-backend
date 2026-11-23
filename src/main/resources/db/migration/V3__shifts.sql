-- Phase 1: company-defined shift master (no seeded defaults).

CREATE TABLE shifts (
  id                    BIGSERIAL PRIMARY KEY,
  company_id            BIGINT NOT NULL,
  name                  VARCHAR(80) NOT NULL,
  start_time            TIME NOT NULL,
  end_time              TIME NOT NULL,
  is_overnight          BOOLEAN NOT NULL DEFAULT FALSE,
  grace_late_minutes    INT NOT NULL DEFAULT 0,
  full_day_hours        NUMERIC(4,2) NOT NULL,
  half_day_hours        NUMERIC(4,2) NOT NULL,
  break_minutes         INT NOT NULL DEFAULT 0,
  break_paid            BOOLEAN NOT NULL DEFAULT FALSE,
  is_default            BOOLEAN NOT NULL DEFAULT FALSE,
  is_active             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_shift_company_name UNIQUE (company_id, name),
  CONSTRAINT fk_shift_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT chk_shift_grace CHECK (grace_late_minutes >= 0),
  CONSTRAINT chk_shift_break CHECK (break_minutes >= 0),
  CONSTRAINT chk_shift_full_hours CHECK (full_day_hours > 0),
  CONSTRAINT chk_shift_half_hours CHECK (half_day_hours >= 0)
);

CREATE INDEX idx_shifts_company_active ON shifts (company_id, is_active);

-- At most one active default shift per company.
CREATE UNIQUE INDEX uq_shift_company_default
  ON shifts (company_id)
  WHERE is_default = TRUE AND is_active = TRUE;
