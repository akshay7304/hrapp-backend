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
 * Leave-request list reads with user / leave-type / actioner joins.
 * Paging runs in Postgres ({@code LIMIT}/{@code OFFSET} + matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class LeaveJdbcRepository {

    private static final String LEAVE_SELECT = """
            SELECT lr.id,
                   lr.user_id,
                   u.full_name,
                   u.emp_code,
                   lr.leave_type_id,
                   lt.name AS leave_type_name,
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
            """;

    private static final RowMapper<LeaveRequestRow> ROW_MAPPER = (rs, rowNum) -> new LeaveRequestRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getLong("leave_type_id"),
            rs.getString("leave_type_name"),
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

    private final JdbcAccess jdbcAccess;

    public List<LeaveRequestRow> getLeaveRequestsByUserAndFromDateRange(
            Long userId, LocalDate from, LocalDate to, int size, long offset) {
        String sql = LEAVE_SELECT
                + """
                  WHERE lr.user_id = :userId
                    AND lr.from_date BETWEEN :fromDate AND :toDate
                  ORDER BY lr.applied_at DESC, lr.id DESC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = rangeParams(userId, from, to, size, offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ROW_MAPPER);
    }

    public long getLeaveRequestsByUserAndFromDateRangeCount(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*)
                FROM leave_requests lr
                WHERE lr.user_id = :userId
                  AND lr.from_date BETWEEN :fromDate AND :toDate
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<LeaveRequestRow> getPendingLeaveRequestsByCompanyExcludingUser(
            Long companyId, Long excludeUserId, int size, long offset) {
        String sql = LEAVE_SELECT
                + """
                  WHERE u.company_id = :companyId
                    AND lr.status = 'PENDING'
                    AND lr.user_id <> :excludeUserId
                  ORDER BY lr.applied_at ASC, lr.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludeUserId", excludeUserId)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ROW_MAPPER);
    }

    public long getPendingLeaveRequestsByCompanyExcludingUserCount(Long companyId, Long excludeUserId) {
        String sql = """
                SELECT COUNT(*)
                FROM leave_requests lr
                JOIN users u ON u.id = lr.user_id
                WHERE u.company_id = :companyId
                  AND lr.status = 'PENDING'
                  AND lr.user_id <> :excludeUserId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("excludeUserId", excludeUserId);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<LeaveRequestRow> getCompanyLeaveRequestsFiltered(
            Long companyId,
            LocalDate from,
            LocalDate to,
            String status,
            Long employeeId,
            int size,
            long offset) {
        StringBuilder sql = new StringBuilder(LEAVE_SELECT);
        MapSqlParameterSource params = buildCompanyFilter(sql, companyId, from, to, status, employeeId);
        sql.append(" ORDER BY lr.applied_at DESC, lr.id DESC LIMIT :size OFFSET :offset");
        params.addValue("size", size).addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql.toString(), params, ROW_MAPPER);
    }

    public long getCompanyLeaveRequestsFilteredCount(
            Long companyId, LocalDate from, LocalDate to, String status, Long employeeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM leave_requests lr
                JOIN users u ON u.id = lr.user_id
                """);
        MapSqlParameterSource params = buildCompanyFilter(sql, companyId, from, to, status, employeeId);
        Long total = jdbcAccess.findLongNamed(sql.toString(), params);
        return total != null ? total : 0L;
    }

    private static MapSqlParameterSource buildCompanyFilter(
            StringBuilder sql,
            Long companyId,
            LocalDate from,
            LocalDate to,
            String status,
            Long employeeId) {
        sql.append("""
                WHERE u.company_id = :companyId
                  AND lr.from_date BETWEEN :fromDate AND :toDate
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        if (status != null && !status.isBlank()) {
            sql.append(" AND lr.status = :status");
            params.addValue("status", status);
        }
        if (employeeId != null) {
            sql.append(" AND lr.user_id = :employeeId");
            params.addValue("employeeId", employeeId);
        }
        return params;
    }

    private static MapSqlParameterSource rangeParams(
            Long userId, LocalDate from, LocalDate to, int size, long offset) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("size", size)
                .addValue("offset", offset);
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
