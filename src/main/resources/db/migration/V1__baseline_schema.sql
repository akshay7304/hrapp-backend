-- ============================================================
-- V1 baseline — PostgreSQL (Supabase) schema
-- Squashed final schema (former MySQL V1–V5). Fresh DBs only.
-- ============================================================

CREATE TABLE companies (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(150) NOT NULL,
  address         TEXT,
  logo_url        VARCHAR(255),
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(50) NOT NULL,
  description     VARCHAR(255),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_role_name UNIQUE (name)
);

CREATE TABLE user_statuses (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(80) NOT NULL,
  description     VARCHAR(255),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_status_name UNIQUE (name)
);

CREATE TABLE departments (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(100) NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_dept_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);
CREATE INDEX fk_dept_company_idx ON departments (company_id);

CREATE TABLE designations (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(100) NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_designation UNIQUE (company_id, name),
  CONSTRAINT fk_desig_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE users (
  id                      BIGSERIAL PRIMARY KEY,
  company_id              BIGINT,
  department_id           BIGINT,
  designation_id          BIGINT,
  status_id               BIGINT NOT NULL,
  emp_code                VARCHAR(20),
  full_name               VARCHAR(150) NOT NULL,
  mobile                  VARCHAR(15) NOT NULL,
  email                   VARCHAR(150),
  password_hash           VARCHAR(255) NOT NULL,
  salary_type             VARCHAR(20) DEFAULT 'MONTHLY',
  joining_date            DATE,
  thumb_device_id         VARCHAR(50),
  profile_pic_url         VARCHAR(255),
  tokens_revoked_before   TIMESTAMP,
  created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  manager_id              BIGINT,
  CONSTRAINT uq_mobile UNIQUE (mobile),
  CONSTRAINT uq_emp_code UNIQUE (company_id, emp_code),
  CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
  CONSTRAINT fk_user_designation FOREIGN KEY (designation_id) REFERENCES designations (id) ON DELETE SET NULL,
  CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_user_status FOREIGN KEY (status_id) REFERENCES user_statuses (id) ON DELETE RESTRICT
);
CREATE INDEX idx_users_company ON users (company_id, status_id);
CREATE INDEX idx_users_thumb ON users (thumb_device_id);
CREATE INDEX idx_users_mobile ON users (mobile);

CREATE TABLE user_roles (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  role_id         BIGINT NOT NULL,
  assigned_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_user_role UNIQUE (user_id, role_id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_roles_user ON user_roles (user_id);

CREATE TABLE company_settings (
  id                        BIGSERIAL PRIMARY KEY,
  company_id                BIGINT NOT NULL,
  shift_start_time          TIME NOT NULL DEFAULT '09:00:00',
  shift_end_time            TIME NOT NULL DEFAULT '18:00:00',
  shift_hours               NUMERIC(4,2) NOT NULL DEFAULT 9.00,
  half_day_hours            NUMERIC(4,2) NOT NULL DEFAULT 4.50,
  overtime_after_hours      NUMERIC(4,2) NOT NULL DEFAULT 9.00,
  late_mark_after_minutes   INT NOT NULL DEFAULT 15,
  week_off_day              VARCHAR(100) NOT NULL DEFAULT 'SUNDAY',
  created_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  leave_count_type          VARCHAR(50) NOT NULL DEFAULT 'EXCLUDE_WEEK_OFF_AND_HOLIDAYS',
  device_brand              VARCHAR(20) NOT NULL DEFAULT 'ZKTECO',
  device_secret             VARCHAR(100) NOT NULL,
  device_serial             VARCHAR(100),
  CONSTRAINT uq_company_settings UNIQUE (company_id),
  CONSTRAINT uq_settings_device_serial UNIQUE (device_serial),
  CONSTRAINT fk_settings_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE holidays (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(100) NOT NULL,
  holiday_date    DATE NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_holiday_date UNIQUE (company_id, holiday_date),
  CONSTRAINT fk_holiday_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE leave_types (
  id                      BIGSERIAL PRIMARY KEY,
  company_id              BIGINT NOT NULL,
  name                    VARCHAR(80) NOT NULL,
  annual_quota            INT NOT NULL DEFAULT 0,
  is_paid                 BOOLEAN NOT NULL DEFAULT TRUE,
  created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  allow_half_day          BOOLEAN NOT NULL DEFAULT FALSE,
  carry_forward           BOOLEAN NOT NULL DEFAULT FALSE,
  max_carry_forward_days  INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_leavetype_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);
CREATE INDEX fk_leavetype_company_idx ON leave_types (company_id);

CREATE TABLE leave_balances (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  leave_type_id   BIGINT NOT NULL,
  year            INTEGER NOT NULL,
  total_days      NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  used_days       NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  remaining_days  NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  CONSTRAINT uq_balance UNIQUE (user_id, leave_type_id, year),
  CONSTRAINT fk_balance_leavetype FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE CASCADE,
  CONSTRAINT fk_balance_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_leave_balances_user_year ON leave_balances (user_id, year);

CREATE TABLE leave_requests (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  leave_type_id   BIGINT NOT NULL,
  from_date       DATE NOT NULL,
  to_date         DATE NOT NULL,
  total_days      NUMERIC(5,1) NOT NULL,
  reason          TEXT,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  actioned_by     BIGINT,
  reject_reason   TEXT,
  applied_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actioned_at     TIMESTAMP,
  is_half_day     BOOLEAN NOT NULL DEFAULT FALSE,
  half_day_type   VARCHAR(20),
  CONSTRAINT fk_leavereq_actioned FOREIGN KEY (actioned_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_leavereq_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
  CONSTRAINT fk_leavereq_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_leavereq_user_status ON leave_requests (user_id, status);

CREATE TABLE attendance (
  id                BIGSERIAL PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  company_id        BIGINT NOT NULL,
  attendance_date   DATE NOT NULL,
  check_in          TIMESTAMP,
  check_out         TIMESTAMP,
  worked_hours      NUMERIC(4,2) NOT NULL DEFAULT 0.00,
  overtime_hours    NUMERIC(4,2) NOT NULL DEFAULT 0.00,
  status            VARCHAR(20) NOT NULL DEFAULT 'ABSENT',
  source            VARCHAR(20) NOT NULL DEFAULT 'MOBILE',
  is_manual         BOOLEAN NOT NULL DEFAULT FALSE,
  manual_reason     TEXT,
  marked_by         BIGINT,
  is_auto_checkout  BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uq_attendance UNIQUE (user_id, attendance_date),
  CONSTRAINT fk_att_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT fk_att_marked_by FOREIGN KEY (marked_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_att_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_att_company_date ON attendance (company_id, attendance_date);
CREATE INDEX idx_att_user_date ON attendance (user_id, attendance_date);

CREATE TABLE attendance_punches (
  id              BIGSERIAL PRIMARY KEY,
  attendance_id   BIGINT NOT NULL,
  user_id         BIGINT NOT NULL,
  punch_time      TIMESTAMP NOT NULL,
  punch_type      VARCHAR(10) NOT NULL,
  source          VARCHAR(20) NOT NULL DEFAULT 'MOBILE',
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_punch_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (id) ON DELETE CASCADE,
  CONSTRAINT fk_punch_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_punch_attendance ON attendance_punches (attendance_id);
CREATE INDEX idx_punch_user ON attendance_punches (user_id, punch_time);

CREATE TABLE thumb_logs (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  device_emp_id   VARCHAR(50) NOT NULL,
  punch_time      TIMESTAMP NOT NULL,
  punch_type      VARCHAR(10) NOT NULL,
  is_processed    BOOLEAN NOT NULL DEFAULT FALSE,
  synced_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_thumblog_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);
CREATE INDEX idx_thumb_company_proc ON thumb_logs (company_id, is_processed, punch_time);

CREATE TABLE advances (
  id                  BIGSERIAL PRIMARY KEY,
  user_id             BIGINT NOT NULL,
  amount              NUMERIC(10,2) NOT NULL,
  reason              TEXT,
  reject_reason       TEXT,
  status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  deduct_from_month   INTEGER,
  deduct_from_year    INTEGER,
  is_recovered        BOOLEAN NOT NULL DEFAULT FALSE,
  approved_by         BIGINT,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actioned_at         TIMESTAMP,
  CONSTRAINT fk_advance_approved FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_advance_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_advance_recovery ON advances (user_id, status, is_recovered);

CREATE TABLE salary_structures (
  id                BIGSERIAL PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  basic             NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  hra               NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  allowances        NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  pf_deduction      NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  other_deductions  NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  overtime_rate     NUMERIC(8,2) NOT NULL DEFAULT 0.00,
  effective_from    DATE NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_salstruct_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_salary_structures_user_date ON salary_structures (user_id, effective_from);

CREATE TABLE salary_payments (
  id                    BIGSERIAL PRIMARY KEY,
  user_id               BIGINT NOT NULL,
  salary_structure_id   BIGINT NOT NULL,
  month                 INTEGER NOT NULL,
  year                  INTEGER NOT NULL,
  working_days          INT NOT NULL,
  present_days          NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  absent_days           NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  half_days             NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  paid_leave_days       NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  unpaid_leave_days     NUMERIC(5,1) NOT NULL DEFAULT 0.0,
  overtime_hours        NUMERIC(5,2) NOT NULL DEFAULT 0.00,
  overtime_pay          NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  gross_salary          NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  advance_deduction     NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  total_deductions      NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  net_salary            NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  paid_on               DATE,
  payslip_url           VARCHAR(255),
  remarks               TEXT,
  generated_by          BIGINT,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_salary UNIQUE (user_id, month, year),
  CONSTRAINT fk_salpay_generated FOREIGN KEY (generated_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_salpay_structure FOREIGN KEY (salary_structure_id) REFERENCES salary_structures (id) ON DELETE RESTRICT,
  CONSTRAINT fk_salpay_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_salpay_user_month ON salary_payments (user_id, year, month);

CREATE TABLE salary_adjustments (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  company_id      BIGINT NOT NULL,
  month           INTEGER NOT NULL,
  year            INTEGER NOT NULL,
  type            VARCHAR(20) NOT NULL,
  amount          NUMERIC(10,2) NOT NULL,
  reason          VARCHAR(255) NOT NULL,
  created_by      BIGINT NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_adj_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
  CONSTRAINT fk_adj_created FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT fk_adj_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_adj_user_month ON salary_adjustments (user_id, month, year);
CREATE INDEX idx_adj_company_month ON salary_adjustments (company_id, month, year);

-- Platform lookup seed
INSERT INTO roles (id, name, description) VALUES
  (1, 'SUPERADMIN', 'App maker - full platform access'),
  (2, 'ADMIN', 'Company owner - full access within their company'),
  (3, 'HR', 'HR manager - day to day HR operations'),
  (4, 'EMPLOYEE', 'Regular staff - own data only');

INSERT INTO user_statuses (id, name, description) VALUES
  (1, 'Active', 'Currently employed and working'),
  (2, 'Probation', 'On probation period'),
  (3, 'On Notice Period', 'Serving notice before leaving'),
  (4, 'Inactive', 'Temporarily inactive'),
  (5, 'Suspended', 'Suspended from duties'),
  (6, 'Resigned', 'Has submitted resignation'),
  (7, 'Terminated', 'Employment terminated by company'),
  (8, 'Contract', 'On contract basis');

SELECT setval(pg_get_serial_sequence('roles', 'id'), (SELECT MAX(id) FROM roles));
SELECT setval(pg_get_serial_sequence('user_statuses', 'id'), (SELECT MAX(id) FROM user_statuses));
