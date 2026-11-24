-- Phase 4: shift rotation definition, members, and draft→confirm roster.

CREATE TABLE shift_rotations (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(80) NOT NULL,
  period_unit     VARCHAR(16) NOT NULL,
  period_length   INT NOT NULL DEFAULT 1,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_shift_rotation_company_name UNIQUE (company_id, name),
  CONSTRAINT fk_shift_rotation_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT chk_shift_rotation_period_unit CHECK (period_unit IN ('WEEKLY', 'DAYS')),
  CONSTRAINT chk_shift_rotation_period_length CHECK (period_length >= 1)
);

CREATE INDEX idx_shift_rotations_company_active ON shift_rotations (company_id, is_active);

CREATE TABLE shift_rotation_steps (
  id            BIGSERIAL PRIMARY KEY,
  rotation_id   BIGINT NOT NULL,
  shift_id      BIGINT NOT NULL,
  step_order    INT NOT NULL,
  CONSTRAINT uq_rotation_step_order UNIQUE (rotation_id, step_order),
  CONSTRAINT fk_rotation_step_rotation FOREIGN KEY (rotation_id) REFERENCES shift_rotations (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_step_shift FOREIGN KEY (shift_id) REFERENCES shifts (id) ON DELETE RESTRICT,
  CONSTRAINT chk_rotation_step_order CHECK (step_order >= 0)
);

CREATE INDEX idx_rotation_steps_rotation ON shift_rotation_steps (rotation_id, step_order);

CREATE TABLE shift_rotation_members (
  id            BIGSERIAL PRIMARY KEY,
  rotation_id   BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  joined_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_rotation_member UNIQUE (rotation_id, user_id),
  CONSTRAINT uq_rotation_member_user UNIQUE (user_id),
  CONSTRAINT fk_rotation_member_rotation FOREIGN KEY (rotation_id) REFERENCES shift_rotations (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_rotation_members_rotation ON shift_rotation_members (rotation_id);

CREATE TABLE shift_rotation_drafts (
  id              BIGSERIAL PRIMARY KEY,
  rotation_id     BIGINT NOT NULL,
  company_id      BIGINT NOT NULL,
  period_start    DATE NOT NULL,
  period_end      DATE NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  confirmed_at    TIMESTAMP,
  confirmed_by    BIGINT,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rotation_draft_rotation FOREIGN KEY (rotation_id) REFERENCES shift_rotations (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_draft_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_draft_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT chk_rotation_draft_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
  CONSTRAINT chk_rotation_draft_period CHECK (period_end >= period_start)
);

-- At most one open draft per rotation.
CREATE UNIQUE INDEX uq_rotation_open_draft
  ON shift_rotation_drafts (rotation_id)
  WHERE status = 'DRAFT';

CREATE INDEX idx_rotation_drafts_rotation_status ON shift_rotation_drafts (rotation_id, status);

CREATE TABLE shift_rotation_draft_rows (
  id                   BIGSERIAL PRIMARY KEY,
  draft_id             BIGINT NOT NULL,
  user_id              BIGINT NOT NULL,
  current_shift_id     BIGINT,
  proposed_shift_id    BIGINT NOT NULL,
  CONSTRAINT uq_rotation_draft_row_user UNIQUE (draft_id, user_id),
  CONSTRAINT fk_rotation_draft_row_draft FOREIGN KEY (draft_id) REFERENCES shift_rotation_drafts (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_draft_row_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_rotation_draft_row_current FOREIGN KEY (current_shift_id) REFERENCES shifts (id) ON DELETE SET NULL,
  CONSTRAINT fk_rotation_draft_row_proposed FOREIGN KEY (proposed_shift_id) REFERENCES shifts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_rotation_draft_rows_draft ON shift_rotation_draft_rows (draft_id);
