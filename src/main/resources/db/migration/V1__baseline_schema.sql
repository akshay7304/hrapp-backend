-- ============================================================
-- V1 baseline — full schema matching current production entities
-- Existing non-empty DBs are baselined at v1 (this script is skipped).
-- Fresh empty DBs run this script, then Hibernate validates.
-- ============================================================

CREATE TABLE `companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(150) NOT NULL,
  `address` text,
  `logo_url` varchar(255) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_statuses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_status_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `departments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_dept_company` (`company_id`),
  CONSTRAINT `fk_dept_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `designations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_designation` (`company_id`,`name`),
  CONSTRAINT `fk_desig_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `designation_id` bigint DEFAULT NULL,
  `status_id` bigint NOT NULL,
  `emp_code` varchar(20) DEFAULT NULL,
  `full_name` varchar(150) NOT NULL,
  `mobile` varchar(15) NOT NULL,
  `email` varchar(150) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `salary_type` enum('MONTHLY','DAILY','HOURLY') DEFAULT 'MONTHLY',
  `joining_date` date DEFAULT NULL,
  `thumb_device_id` varchar(50) DEFAULT NULL,
  `profile_pic_url` varchar(255) DEFAULT NULL,
  `tokens_revoked_before` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mobile` (`mobile`),
  UNIQUE KEY `uq_emp_code` (`company_id`,`emp_code`),
  KEY `fk_user_department` (`department_id`),
  KEY `fk_user_designation` (`designation_id`),
  KEY `fk_user_status` (`status_id`),
  KEY `idx_users_company` (`company_id`,`status_id`),
  KEY `idx_users_thumb` (`thumb_device_id`),
  KEY `fk_user_manager` (`manager_id`),
  KEY `idx_users_mobile` (`mobile`),
  CONSTRAINT `fk_user_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_designation` FOREIGN KEY (`designation_id`) REFERENCES `designations` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_manager` FOREIGN KEY (`manager_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_status` FOREIGN KEY (`status_id`) REFERENCES `user_statuses` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `assigned_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_role` (`user_id`,`role_id`),
  KEY `fk_ur_role` (`role_id`),
  KEY `idx_user_roles_user` (`user_id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `company_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `shift_start_time` time NOT NULL DEFAULT '09:00:00',
  `shift_end_time` time NOT NULL DEFAULT '18:00:00',
  `shift_hours` decimal(4,2) NOT NULL DEFAULT '9.00',
  `half_day_hours` decimal(4,2) NOT NULL DEFAULT '4.50',
  `overtime_after_hours` decimal(4,2) NOT NULL DEFAULT '9.00',
  `late_mark_after_minutes` int NOT NULL DEFAULT '15',
  `week_off_day` varchar(100) NOT NULL DEFAULT 'SUNDAY',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `leave_count_type` varchar(50) NOT NULL DEFAULT 'EXCLUDE_WEEK_OFF_AND_HOLIDAYS',
  `device_brand` varchar(20) NOT NULL DEFAULT 'ZKTECO',
  `device_secret` varchar(100) NOT NULL,
  `device_serial` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_company_settings` (`company_id`),
  UNIQUE KEY `uq_settings_device_serial` (`device_serial`),
  CONSTRAINT `fk_settings_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `holidays` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `holiday_date` date NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_holiday_date` (`company_id`,`holiday_date`),
  CONSTRAINT `fk_holiday_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(80) NOT NULL,
  `annual_quota` int NOT NULL DEFAULT '0',
  `is_paid` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `allow_half_day` tinyint(1) NOT NULL DEFAULT '0',
  `carry_forward` tinyint(1) NOT NULL DEFAULT '0',
  `max_carry_forward_days` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_leavetype_company` (`company_id`),
  CONSTRAINT `fk_leavetype_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_balances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `leave_type_id` bigint NOT NULL,
  `year` year NOT NULL,
  `total_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `used_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `remaining_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_balance` (`user_id`,`leave_type_id`,`year`),
  KEY `fk_balance_leavetype` (`leave_type_id`),
  KEY `idx_leave_balances_user_year` (`user_id`,`year`),
  CONSTRAINT `fk_balance_leavetype` FOREIGN KEY (`leave_type_id`) REFERENCES `leave_types` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_balance_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `leave_type_id` bigint NOT NULL,
  `from_date` date NOT NULL,
  `to_date` date NOT NULL,
  `total_days` decimal(5,1) NOT NULL,
  `reason` text,
  `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `actioned_by` bigint DEFAULT NULL,
  `reject_reason` text,
  `applied_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actioned_at` timestamp NULL DEFAULT NULL,
  `is_half_day` tinyint(1) NOT NULL DEFAULT '0',
  `half_day_type` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_leavereq_type` (`leave_type_id`),
  KEY `fk_leavereq_actioned` (`actioned_by`),
  KEY `idx_leavereq_user_status` (`user_id`,`status`),
  CONSTRAINT `fk_leavereq_actioned` FOREIGN KEY (`actioned_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_leavereq_type` FOREIGN KEY (`leave_type_id`) REFERENCES `leave_types` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_leavereq_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `attendance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `attendance_date` date NOT NULL,
  `check_in` datetime DEFAULT NULL,
  `check_out` datetime DEFAULT NULL,
  `worked_hours` decimal(4,2) NOT NULL DEFAULT '0.00',
  `overtime_hours` decimal(4,2) NOT NULL DEFAULT '0.00',
  `status` enum('PRESENT','ABSENT','HALF_DAY','ON_LEAVE','HOLIDAY','WEEK_OFF') NOT NULL DEFAULT 'ABSENT',
  `source` enum('THUMB','MOBILE','MANUAL','WEB') NOT NULL DEFAULT 'MOBILE',
  `is_manual` tinyint(1) NOT NULL DEFAULT '0',
  `manual_reason` text,
  `marked_by` bigint DEFAULT NULL,
  `is_auto_checkout` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance` (`user_id`,`attendance_date`),
  KEY `fk_att_marked_by` (`marked_by`),
  KEY `idx_att_company_date` (`company_id`,`attendance_date`),
  KEY `idx_att_user_date` (`user_id`,`attendance_date`),
  CONSTRAINT `fk_att_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_att_marked_by` FOREIGN KEY (`marked_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_att_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `attendance_punches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attendance_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `punch_time` datetime NOT NULL,
  `punch_type` enum('IN','OUT') NOT NULL,
  `source` enum('MOBILE','THUMB','MANUAL','WEB') NOT NULL DEFAULT 'MOBILE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_punch_attendance` (`attendance_id`),
  KEY `idx_punch_user` (`user_id`,`punch_time`),
  CONSTRAINT `fk_punch_attendance` FOREIGN KEY (`attendance_id`) REFERENCES `attendance` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_punch_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `thumb_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `device_emp_id` varchar(50) NOT NULL,
  `punch_time` datetime NOT NULL,
  `punch_type` enum('IN','OUT') NOT NULL,
  `is_processed` tinyint(1) NOT NULL DEFAULT '0',
  `synced_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_thumb_company_proc` (`company_id`,`is_processed`,`punch_time`),
  CONSTRAINT `fk_thumblog_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `advances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` text,
  `reject_reason` text,
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `deduct_from_month` tinyint DEFAULT NULL,
  `deduct_from_year` smallint DEFAULT NULL,
  `is_recovered` tinyint(1) NOT NULL DEFAULT '0',
  `approved_by` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actioned_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_advance_approved` (`approved_by`),
  KEY `idx_advance_recovery` (`user_id`,`status`,`is_recovered`),
  CONSTRAINT `fk_advance_approved` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_advance_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_structures` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `basic` decimal(10,2) NOT NULL DEFAULT '0.00',
  `hra` decimal(10,2) NOT NULL DEFAULT '0.00',
  `allowances` decimal(10,2) NOT NULL DEFAULT '0.00',
  `pf_deduction` decimal(10,2) NOT NULL DEFAULT '0.00',
  `other_deductions` decimal(10,2) NOT NULL DEFAULT '0.00',
  `overtime_rate` decimal(8,2) NOT NULL DEFAULT '0.00',
  `effective_from` date NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_salary_structures_user_date` (`user_id`,`effective_from`),
  CONSTRAINT `fk_salstruct_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `salary_structure_id` bigint NOT NULL,
  `month` tinyint NOT NULL,
  `year` smallint NOT NULL,
  `working_days` int NOT NULL,
  `present_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `absent_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `half_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `paid_leave_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `unpaid_leave_days` decimal(5,1) NOT NULL DEFAULT '0.0',
  `overtime_hours` decimal(5,2) NOT NULL DEFAULT '0.00',
  `overtime_pay` decimal(10,2) NOT NULL DEFAULT '0.00',
  `gross_salary` decimal(10,2) NOT NULL DEFAULT '0.00',
  `advance_deduction` decimal(10,2) NOT NULL DEFAULT '0.00',
  `total_deductions` decimal(10,2) NOT NULL DEFAULT '0.00',
  `net_salary` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` enum('DRAFT','PAID') NOT NULL DEFAULT 'DRAFT',
  `paid_on` date DEFAULT NULL,
  `payslip_url` varchar(255) DEFAULT NULL,
  `remarks` text,
  `generated_by` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_salary` (`user_id`,`month`,`year`),
  KEY `fk_salpay_structure` (`salary_structure_id`),
  KEY `fk_salpay_generated` (`generated_by`),
  KEY `idx_salpay_user_month` (`user_id`,`year`,`month`),
  CONSTRAINT `fk_salpay_generated` FOREIGN KEY (`generated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_salpay_structure` FOREIGN KEY (`salary_structure_id`) REFERENCES `salary_structures` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_salpay_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_adjustments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `month` tinyint NOT NULL,
  `year` smallint NOT NULL,
  `type` varchar(20) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(255) NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_adj_created` (`created_by`),
  KEY `idx_adj_user_month` (`user_id`,`month`,`year`),
  KEY `idx_adj_company_month` (`company_id`,`month`,`year`),
  CONSTRAINT `fk_adj_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_adj_created` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_adj_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Platform lookup seed (required for auth / employee create on a fresh DB)
INSERT INTO `roles` (`id`, `name`, `description`) VALUES
  (1, 'SUPERADMIN', 'App maker - full platform access'),
  (2, 'ADMIN', 'Company owner - full access within their company'),
  (3, 'HR', 'HR manager - day to day HR operations'),
  (4, 'EMPLOYEE', 'Regular staff - own data only');

INSERT INTO `user_statuses` (`id`, `name`, `description`) VALUES
  (1, 'Active', 'Currently employed and working'),
  (2, 'Probation', 'On probation period'),
  (3, 'On Notice Period', 'Serving notice before leaving'),
  (4, 'Inactive', 'Temporarily inactive'),
  (5, 'Suspended', 'Suspended from duties'),
  (6, 'Resigned', 'Has submitted resignation'),
  (7, 'Terminated', 'Employment terminated by company'),
  (8, 'Contract', 'On contract basis');
