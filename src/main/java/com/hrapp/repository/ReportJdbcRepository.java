package com.hrapp.repository;

import com.hrapp.jdbc.JdbcAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Report list reads with joins / aggregates. Paging runs in Postgres
 * ({@code LIMIT}/{@code OFFSET} + matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class ReportJdbcRepository {

    private static final String EMPLOYEE_SELECT = """
            SELECT u.id AS user_id,
                   u.full_name,
                   u.emp_code,
                   d.name AS department_name,
                   des.name AS designation_name
            FROM users u
            LEFT JOIN departments d ON d.id = u.department_id
            LEFT JOIN designations des ON des.id = u.designation_id
            """;

    private static final RowMapper<ReportEmployeeRow> EMPLOYEE_MAPPER = (rs, rowNum) -> new ReportEmployeeRow(
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getString("department_name"),
            rs.getString("designation_name"));

    private static final RowMapper<SalaryReportRow> SALARY_MAPPER = (rs, rowNum) -> new SalaryReportRow(
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getString("department_name"),
            rs.getString("designation_name"),
            rs.getInt("month"),
            rs.getInt("year"),
            rs.getString("salary_type"),
            rs.getBigDecimal("basic"),
            rs.getBigDecimal("hra"),
            rs.getBigDecimal("allowances"),
            rs.getBigDecimal("gross_salary"),
            rs.getBigDecimal("pf_deduction"),
            rs.getBigDecimal("other_deductions"),
            rs.getBigDecimal("advance_deduction"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("net_salary"),
            rs.getString("status"),
            rs.getObject("paid_on", LocalDate.class));

    private static final RowMapper<AdvanceReportUserRow> ADVANCE_USER_MAPPER = (rs, rowNum) -> new AdvanceReportUserRow(
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getString("department_name"),
            rs.getBigDecimal("total_requested"),
            rs.getBigDecimal("total_approved"),
            rs.getBigDecimal("total_recovered"),
            rs.getBigDecimal("total_pending"));

    private static final RowMapper<LeaveReportLeaveRow> LEAVE_MAPPER = (rs, rowNum) -> new LeaveReportLeaveRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getLong("leave_type_id"),
            rs.getString("leave_type_name"),
            nullableBoolean(rs, "leave_type_is_paid"),
            rs.getObject("from_date", LocalDate.class),
            rs.getObject("to_date", LocalDate.class),
            rs.getBigDecimal("total_days"),
            nullableBoolean(rs, "is_half_day"),
            rs.getString("half_day_type"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getString("reject_reason"),
            rs.getString("actioned_by_name"),
            rs.getObject("applied_at", LocalDateTime.class),
            rs.getObject("actioned_at", LocalDateTime.class));

    private static final RowMapper<AttendanceReportDayRow> ATTENDANCE_DAY_MAPPER =
            (rs, rowNum) -> new AttendanceReportDayRow(
                    rs.getLong("user_id"),
                    rs.getObject("attendance_date", LocalDate.class),
                    rs.getObject("check_out", LocalDateTime.class),
                    rs.getString("status"),
                    rs.getBigDecimal("overtime_hours"));

    private static final RowMapper<AdjustmentTotalsRow> ADJUSTMENT_TOTALS_MAPPER =
            (rs, rowNum) -> new AdjustmentTotalsRow(
                    rs.getLong("user_id"),
                    rs.getBigDecimal("additions"),
                    rs.getBigDecimal("deductions"));

    private static final RowMapper<AdvanceRow> ADVANCE_MAPPER = (rs, rowNum) -> new AdvanceRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getBigDecimal("amount"),
            rs.getString("reason"),
            rs.getString("reject_reason"),
            rs.getString("status"),
            nullableInt(rs, "deduct_from_month"),
            nullableInt(rs, "deduct_from_year"),
            nullableBoolean(rs, "is_recovered"),
            rs.getString("approved_by_name"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("actioned_at", LocalDateTime.class));

    private final JdbcAccess jdbcAccess;

    // ---- Attendance report: page eligible employees ----

    public List<ReportEmployeeRow> getAttendanceReportEmployees(
            Long companyId, Collection<String> excludedStatuses, int size, long offset) {
        String sql = EMPLOYEE_SELECT
                + """
                  JOIN user_statuses st ON st.id = u.status_id
                  WHERE u.company_id = :companyId
                    AND st.name NOT IN (:excludedStatuses)
                  ORDER BY u.emp_code ASC NULLS LAST, u.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludedStatuses", excludedStatuses)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, EMPLOYEE_MAPPER);
    }

    public long getAttendanceReportEmployeesCount(Long companyId, Collection<String> excludedStatuses) {
        String sql = """
                SELECT COUNT(*)
                FROM users u
                JOIN user_statuses st ON st.id = u.status_id
                WHERE u.company_id = :companyId
                  AND st.name NOT IN (:excludedStatuses)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludedStatuses", excludedStatuses);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<AttendanceReportDayRow> getAttendanceDaysByUsersAndDateRange(
            Collection<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT a.user_id,
                       a.attendance_date,
                       a.check_out,
                       a.status,
                       a.overtime_hours
                FROM attendance a
                WHERE a.user_id IN (:userIds)
                  AND a.attendance_date BETWEEN :fromDate AND :toDate
                ORDER BY a.user_id ASC, a.attendance_date ASC, a.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        return jdbcAccess.findUsingNamedParameters(sql, params, ATTENDANCE_DAY_MAPPER);
    }

    public List<LeaveReportLeaveRow> getApprovedOverlappingLeavesByUsers(
            Collection<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT lr.id,
                       lr.user_id,
                       u.full_name,
                       u.emp_code,
                       lr.leave_type_id,
                       lt.name AS leave_type_name,
                       lt.is_paid AS leave_type_is_paid,
                       lr.from_date,
                       lr.to_date,
                       lr.total_days,
                       lr.is_half_day,
                       lr.half_day_type,
                       lr.reason,
                       lr.status,
                       lr.reject_reason,
                       ab.full_name AS actioned_by_name,
                       lr.applied_at,
                       lr.actioned_at
                FROM leave_requests lr
                JOIN users u ON u.id = lr.user_id
                JOIN leave_types lt ON lt.id = lr.leave_type_id
                LEFT JOIN users ab ON ab.id = lr.actioned_by
                WHERE lr.user_id IN (:userIds)
                  AND lr.status = 'APPROVED'
                  AND lr.from_date <= :toDate
                  AND lr.to_date >= :fromDate
                ORDER BY lr.user_id ASC, lr.from_date ASC, lr.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        return jdbcAccess.findUsingNamedParameters(sql, params, LEAVE_MAPPER);
    }

    // ---- Salary report ----

    public List<SalaryReportRow> getSalaryReportByCompanyMonthYear(
            Long companyId, Integer month, Integer year, int size, long offset) {
        String sql = """
                SELECT sp.user_id,
                       u.full_name,
                       u.emp_code,
                       d.name AS department_name,
                       des.name AS designation_name,
                       sp.month,
                       sp.year,
                       u.salary_type,
                       ss.basic,
                       ss.hra,
                       ss.allowances,
                       sp.gross_salary,
                       ss.pf_deduction,
                       ss.other_deductions,
                       sp.advance_deduction,
                       sp.total_deductions,
                       sp.net_salary,
                       sp.status,
                       sp.paid_on
                FROM salary_payments sp
                JOIN users u ON u.id = sp.user_id
                JOIN salary_structures ss ON ss.id = sp.salary_structure_id
                LEFT JOIN departments d ON d.id = u.department_id
                LEFT JOIN designations des ON des.id = u.designation_id
                WHERE u.company_id = :companyId
                  AND sp.month = :month
                  AND sp.year = :year
                ORDER BY u.emp_code ASC NULLS LAST, u.id ASC
                LIMIT :size OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("month", month)
                .addValue("year", year)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, SALARY_MAPPER);
    }

    public long getSalaryReportByCompanyMonthYearCount(Long companyId, Integer month, Integer year) {
        String sql = """
                SELECT COUNT(*)
                FROM salary_payments sp
                JOIN users u ON u.id = sp.user_id
                WHERE u.company_id = :companyId
                  AND sp.month = :month
                  AND sp.year = :year
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("month", month)
                .addValue("year", year);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<AdjustmentTotalsRow> getAdjustmentTotalsByCompanyMonthYear(
            Long companyId, Integer month, Integer year) {
        String sql = """
                SELECT sa.user_id,
                       COALESCE(SUM(CASE WHEN upper(sa.type) = 'ADDITION' THEN sa.amount ELSE 0 END), 0)
                           AS additions,
                       COALESCE(SUM(CASE WHEN upper(sa.type) = 'DEDUCTION' THEN sa.amount ELSE 0 END), 0)
                           AS deductions
                FROM salary_adjustments sa
                WHERE sa.company_id = :companyId
                  AND sa.month = :month
                  AND sa.year = :year
                GROUP BY sa.user_id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("month", month)
                .addValue("year", year);
        return jdbcAccess.findUsingNamedParameters(sql, params, ADJUSTMENT_TOTALS_MAPPER);
    }

    // ---- Leave report ----

    public List<ReportEmployeeRow> getLeaveReportEmployees(Long companyId, int size, long offset) {
        String sql = EMPLOYEE_SELECT
                + """
                  WHERE u.company_id = :companyId
                  ORDER BY u.emp_code ASC NULLS LAST, u.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, EMPLOYEE_MAPPER);
    }

    public long getLeaveReportEmployeesCount(Long companyId) {
        String sql = "SELECT COUNT(*) FROM users u WHERE u.company_id = :companyId";
        Long total = jdbcAccess.findLongNamed(sql, new MapSqlParameterSource("companyId", companyId));
        return total != null ? total : 0L;
    }

    public List<LeaveReportLeaveRow> getOverlappingLeavesByUsers(
            Collection<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT lr.id,
                       lr.user_id,
                       u.full_name,
                       u.emp_code,
                       lr.leave_type_id,
                       lt.name AS leave_type_name,
                       lt.is_paid AS leave_type_is_paid,
                       lr.from_date,
                       lr.to_date,
                       lr.total_days,
                       lr.is_half_day,
                       lr.half_day_type,
                       lr.reason,
                       lr.status,
                       lr.reject_reason,
                       ab.full_name AS actioned_by_name,
                       lr.applied_at,
                       lr.actioned_at
                FROM leave_requests lr
                JOIN users u ON u.id = lr.user_id
                JOIN leave_types lt ON lt.id = lr.leave_type_id
                LEFT JOIN users ab ON ab.id = lr.actioned_by
                WHERE lr.user_id IN (:userIds)
                  AND lr.from_date <= :toDate
                  AND lr.to_date >= :fromDate
                ORDER BY lr.user_id ASC, lr.applied_at ASC, lr.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userIds", userIds)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        return jdbcAccess.findUsingNamedParameters(sql, params, LEAVE_MAPPER);
    }

    // ---- Advance report ----

    public List<AdvanceReportUserRow> getAdvanceReportUsers(Long companyId, int size, long offset) {
        String sql = """
                SELECT u.id AS user_id,
                       u.full_name,
                       u.emp_code,
                       d.name AS department_name,
                       COALESCE(SUM(a.amount), 0) AS total_requested,
                       COALESCE(SUM(CASE WHEN a.status = 'APPROVED' THEN a.amount ELSE 0 END), 0)
                           AS total_approved,
                       COALESCE(SUM(CASE WHEN a.is_recovered IS TRUE THEN a.amount ELSE 0 END), 0)
                           AS total_recovered,
                       COALESCE(SUM(CASE WHEN a.status = 'PENDING' THEN a.amount ELSE 0 END), 0)
                           AS total_pending
                FROM advances a
                JOIN users u ON u.id = a.user_id
                LEFT JOIN departments d ON d.id = u.department_id
                WHERE u.company_id = :companyId
                GROUP BY u.id, u.full_name, u.emp_code, d.name
                ORDER BY u.emp_code ASC NULLS LAST, u.id ASC
                LIMIT :size OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ADVANCE_USER_MAPPER);
    }

    public long getAdvanceReportUsersCount(Long companyId) {
        String sql = """
                SELECT COUNT(DISTINCT a.user_id)
                FROM advances a
                JOIN users u ON u.id = a.user_id
                WHERE u.company_id = :companyId
                """;
        Long total = jdbcAccess.findLongNamed(sql, new MapSqlParameterSource("companyId", companyId));
        return total != null ? total : 0L;
    }

    public List<AdvanceRow> getAdvancesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT a.id,
                       a.user_id,
                       u.full_name,
                       u.emp_code,
                       a.amount,
                       a.reason,
                       a.reject_reason,
                       a.status,
                       a.deduct_from_month,
                       a.deduct_from_year,
                       a.is_recovered,
                       ap.full_name AS approved_by_name,
                       a.created_at,
                       a.actioned_at
                FROM advances a
                JOIN users u ON u.id = a.user_id
                LEFT JOIN users ap ON ap.id = a.approved_by
                WHERE a.user_id IN (:userIds)
                ORDER BY a.user_id ASC, a.created_at DESC, a.id DESC
                """;
        return jdbcAccess.findUsingNamedParameters(
                sql, new MapSqlParameterSource("userIds", userIds), ADVANCE_MAPPER);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
