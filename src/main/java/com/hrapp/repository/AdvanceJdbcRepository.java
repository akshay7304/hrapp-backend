package com.hrapp.repository;

import com.hrapp.jdbc.JdbcAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Advance list reads with user / approver joins. Paginated endpoints use
 * {@code LIMIT}/{@code OFFSET} plus a matching {@code COUNT}.
 */
@Repository
@RequiredArgsConstructor
public class AdvanceJdbcRepository {

    private static final String ADVANCE_SELECT = """
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
            """;

    private static final RowMapper<AdvanceRow> ROW_MAPPER = (rs, rowNum) -> new AdvanceRow(
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

    public List<AdvanceRow> getAdvancesByUser(Long userId) {
        String sql = ADVANCE_SELECT
                + " WHERE a.user_id = :userId ORDER BY a.created_at DESC, a.id DESC";
        return jdbcAccess.findUsingNamedParameters(
                sql, new MapSqlParameterSource("userId", userId), ROW_MAPPER);
    }

    public List<AdvanceRow> getPendingAdvancesByCompanyExcludingUser(
            Long companyId, Long excludeUserId, int size, long offset) {
        String sql = ADVANCE_SELECT
                + """
                  WHERE u.company_id = :companyId
                    AND a.status = 'PENDING'
                    AND a.user_id <> :excludeUserId
                  ORDER BY a.created_at DESC, a.id DESC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludeUserId", excludeUserId)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ROW_MAPPER);
    }

    public long getPendingAdvancesByCompanyExcludingUserCount(Long companyId, Long excludeUserId) {
        String sql = """
                SELECT COUNT(*)
                FROM advances a
                JOIN users u ON u.id = a.user_id
                WHERE u.company_id = :companyId
                  AND a.status = 'PENDING'
                  AND a.user_id <> :excludeUserId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludeUserId", excludeUserId);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<AdvanceRow> getCompanyAdvancesFiltered(
            Long companyId, String status, Long employeeId, int size, long offset) {
        StringBuilder sql = new StringBuilder(ADVANCE_SELECT);
        MapSqlParameterSource params = buildCompanyFilter(sql, companyId, status, employeeId);
        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT :size OFFSET :offset");
        params.addValue("size", size).addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql.toString(), params, ROW_MAPPER);
    }

    public long getCompanyAdvancesFilteredCount(Long companyId, String status, Long employeeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM advances a
                JOIN users u ON u.id = a.user_id
                """);
        MapSqlParameterSource params = buildCompanyFilter(sql, companyId, status, employeeId);
        Long total = jdbcAccess.findLongNamed(sql.toString(), params);
        return total != null ? total : 0L;
    }

    private static MapSqlParameterSource buildCompanyFilter(
            StringBuilder sql, Long companyId, String status, Long employeeId) {
        sql.append(" WHERE u.company_id = :companyId");
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = :status");
            params.addValue("status", status);
        }
        if (employeeId != null) {
            sql.append(" AND a.user_id = :employeeId");
            params.addValue("employeeId", employeeId);
        }
        return params;
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
