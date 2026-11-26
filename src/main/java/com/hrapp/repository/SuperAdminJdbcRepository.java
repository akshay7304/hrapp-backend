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
import java.util.List;

/**
 * Cross-tenant aggregates for SUPERADMIN home. All paging/limits run in SQL.
 */
@Repository
@RequiredArgsConstructor
public class SuperAdminJdbcRepository {

    private static final RowMapper<PlatformStatsRow> STATS_MAPPER = (rs, rowNum) -> new PlatformStatsRow(
            rs.getLong("total_companies"),
            rs.getLong("active_companies"),
            rs.getLong("inactive_companies"),
            rs.getLong("companies_created_last_30_days"),
            rs.getLong("total_employees"),
            rs.getLong("companies_missing_device_serial"),
            rs.getLong("companies_with_pending_thumb_logs"),
            rs.getLong("companies_without_admin"),
            rs.getLong("companies_with_no_payroll_this_month"),
            rs.getLong("pending_thumb_logs_total"),
            rs.getLong("pending_leave_requests_total"),
            rs.getLong("pending_advances_total"));

    private static final RowMapper<PlatformCompanyCardRow> CARD_MAPPER = (rs, rowNum) -> new PlatformCompanyCardRow(
            rs.getLong("id"),
            rs.getString("name"),
            nullableBoolean(rs, "is_active"),
            rs.getInt("total_employees"),
            rs.getString("admin_name"),
            rs.getString("admin_mobile"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getBoolean("has_settings"),
            rs.getBoolean("has_device_serial"),
            rs.getBoolean("has_admin"),
            rs.getLong("pending_thumb_logs"),
            rs.getBoolean("payroll_run_this_month"));

    private final JdbcAccess jdbcAccess;

    public PlatformStatsRow getPlatformStats(
            LocalDateTime createdSince, int payrollMonth, int payrollYear) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM companies) AS total_companies,
                  (SELECT COUNT(*) FROM companies WHERE is_active IS TRUE) AS active_companies,
                  (SELECT COUNT(*) FROM companies WHERE is_active IS NOT TRUE) AS inactive_companies,
                  (SELECT COUNT(*) FROM companies WHERE created_at >= :createdSince)
                      AS companies_created_last_30_days,
                  (SELECT COUNT(*) FROM users WHERE company_id IS NOT NULL) AS total_employees,
                  (SELECT COUNT(*)
                   FROM companies c
                   LEFT JOIN company_settings cs ON cs.company_id = c.id
                   WHERE cs.id IS NULL
                      OR cs.device_serial IS NULL
                      OR btrim(cs.device_serial) = '') AS companies_missing_device_serial,
                  (SELECT COUNT(DISTINCT tl.company_id)
                   FROM thumb_logs tl
                   WHERE tl.is_processed IS FALSE) AS companies_with_pending_thumb_logs,
                  (SELECT COUNT(*)
                   FROM companies c
                   WHERE NOT EXISTS (
                       SELECT 1
                       FROM users u
                       JOIN user_roles ur ON ur.user_id = u.id
                       JOIN roles r ON r.id = ur.role_id
                       WHERE u.company_id = c.id
                         AND r.name = 'ADMIN'
                   )) AS companies_without_admin,
                  (SELECT COUNT(*)
                   FROM companies c
                   WHERE c.is_active IS TRUE
                     AND NOT EXISTS (
                       SELECT 1
                       FROM salary_payments sp
                       JOIN users u ON u.id = sp.user_id
                       WHERE u.company_id = c.id
                         AND sp.month = :payrollMonth
                         AND sp.year = :payrollYear
                     )) AS companies_with_no_payroll_this_month,
                  (SELECT COUNT(*) FROM thumb_logs WHERE is_processed IS FALSE)
                      AS pending_thumb_logs_total,
                  (SELECT COUNT(*) FROM leave_requests WHERE status = 'PENDING')
                      AS pending_leave_requests_total,
                  (SELECT COUNT(*) FROM advances WHERE status = 'PENDING')
                      AS pending_advances_total
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("createdSince", createdSince)
                .addValue("payrollMonth", payrollMonth)
                .addValue("payrollYear", payrollYear);
        List<PlatformStatsRow> rows = jdbcAccess.findUsingNamedParameters(sql, params, STATS_MAPPER);
        if (rows.isEmpty()) {
            return new PlatformStatsRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return rows.get(0);
    }

    public List<PlatformCompanyCardRow> getRecentCompanies(
            int limit, int payrollMonth, int payrollYear) {
        String sql = """
                SELECT c.id,
                       c.name,
                       c.is_active,
                       COALESCE(emp.total_employees, 0) AS total_employees,
                       adm.full_name AS admin_name,
                       adm.mobile AS admin_mobile,
                       c.created_at,
                       (cs.id IS NOT NULL) AS has_settings,
                       (cs.device_serial IS NOT NULL AND btrim(cs.device_serial) <> '')
                           AS has_device_serial,
                       (adm.user_id IS NOT NULL) AS has_admin,
                       COALESCE(thumb.pending_count, 0) AS pending_thumb_logs,
                       EXISTS (
                           SELECT 1
                           FROM salary_payments sp
                           JOIN users u ON u.id = sp.user_id
                           WHERE u.company_id = c.id
                             AND sp.month = :payrollMonth
                             AND sp.year = :payrollYear
                       ) AS payroll_run_this_month
                FROM companies c
                LEFT JOIN company_settings cs ON cs.company_id = c.id
                LEFT JOIN LATERAL (
                    SELECT COUNT(*)::int AS total_employees
                    FROM users u
                    WHERE u.company_id = c.id
                ) emp ON TRUE
                LEFT JOIN LATERAL (
                    SELECT u.id AS user_id, u.full_name, u.mobile
                    FROM users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id
                    WHERE u.company_id = c.id
                      AND r.name = 'ADMIN'
                    ORDER BY u.id ASC
                    LIMIT 1
                ) adm ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS pending_count
                    FROM thumb_logs tl
                    WHERE tl.company_id = c.id
                      AND tl.is_processed IS FALSE
                ) thumb ON TRUE
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("payrollMonth", payrollMonth)
                .addValue("payrollYear", payrollYear);
        return jdbcAccess.findUsingNamedParameters(sql, params, CARD_MAPPER);
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
