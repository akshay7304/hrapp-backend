-- Targeted indexes for hot home / attendance / payroll count paths.
-- Attendance (user+date, company+date) and holidays (company+date unique) already covered in V1.

-- Last-punch / ordered punch list: attendance_id alone cannot satisfy ORDER BY punch_time.
DROP INDEX IF EXISTS idx_punch_attendance;
CREATE INDEX idx_punch_attendance ON attendance_punches (attendance_id, punch_time);

-- Company pending leave/advance counts: WHERE status = ? then join users.company_id.
-- Existing (user_id, status) indexes serve "my pending"; status-leading helps ops counts.
CREATE INDEX idx_leavereq_status_user ON leave_requests (status, user_id);
CREATE INDEX idx_advance_status_user ON advances (status, user_id);

-- Payroll home summary: filter by month/year(/status) then join users for company_id.
CREATE INDEX idx_salpay_year_month_status ON salary_payments (year, month, status);
