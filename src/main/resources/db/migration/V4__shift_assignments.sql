-- Phase 2: fixed shift assignments with effective dating.

CREATE TABLE shift_assignments (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL,
  user_id          BIGINT NOT NULL,
  shift_id         BIGINT NOT NULL,
  effective_from   DATE NOT NULL,
  created_by       BIGINT,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_shift_assign_user_from UNIQUE (user_id, effective_from),
  CONSTRAINT fk_shift_assign_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT fk_shift_assign_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_shift_assign_shift FOREIGN KEY (shift_id) REFERENCES shifts (id) ON DELETE RESTRICT,
  CONSTRAINT fk_shift_assign_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_shift_assign_user_from ON shift_assignments (user_id, effective_from DESC);
CREATE INDEX idx_shift_assign_company_from ON shift_assignments (company_id, effective_from);
