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
 * Attendance list/history reads with user join. Paging runs in Postgres
 * ({@code LIMIT}/{@code OFFSET} plus a matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class AttendanceJdbcRepository {

    private static final String ATTENDANCE_SELECT = """
            SELECT a.id,
                   a.user_id,
                   u.full_name,
                   u.emp_code,
                   a.company_id,
                   a.attendance_date,
                   a.check_in,
                   a.check_out,
                   a.worked_seconds,
                   a.overtime_seconds,
                   a.status,
                   a.source,
                   a.is_manual,
                   a.is_auto_checkout,
                   a.is_late,
                   a.manual_reason
            FROM attendance a
            JOIN users u ON u.id = a.user_id
            """;

    private static final RowMapper<AttendanceRow> ATTENDANCE_MAPPER = (rs, rowNum) -> new AttendanceRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getLong("company_id"),
            rs.getObject("attendance_date", LocalDate.class),
            rs.getObject("check_in", LocalDateTime.class),
            rs.getObject("check_out", LocalDateTime.class),
            nullableLong(rs, "worked_seconds"),
            nullableLong(rs, "overtime_seconds"),
            rs.getString("status"),
            rs.getString("source"),
            nullableBoolean(rs, "is_manual"),
            nullableBoolean(rs, "is_auto_checkout"),
            nullableBoolean(rs, "is_late"),
            rs.getString("manual_reason"));

    private static final RowMapper<AttendancePunchRow> PUNCH_MAPPER = (rs, rowNum) -> new AttendancePunchRow(
            rs.getLong("id"),
            rs.getLong("attendance_id"),
            rs.getObject("punch_time", LocalDateTime.class),
            rs.getString("punch_type"),
            rs.getString("source"));

    private final JdbcAccess jdbcAccess;

    public List<AttendanceRow> getAttendanceHistoryByUser(
            Long userId, LocalDate from, LocalDate to, int size, long offset) {
        String sql = ATTENDANCE_SELECT
                + """
                  WHERE a.user_id = :userId
                    AND a.attendance_date BETWEEN :fromDate AND :toDate
                  ORDER BY a.attendance_date ASC, a.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ATTENDANCE_MAPPER);
    }

    public long getAttendanceHistoryByUserCount(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*)
                FROM attendance a
                WHERE a.user_id = :userId
                  AND a.attendance_date BETWEEN :fromDate AND :toDate
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<AttendanceRow> getCompanyAttendanceByDate(
            Long companyId, LocalDate date, int size, long offset) {
        String sql = ATTENDANCE_SELECT
                + """
                  WHERE a.company_id = :companyId
                    AND a.attendance_date = :attendanceDate
                  ORDER BY u.full_name ASC, a.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("attendanceDate", date)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ATTENDANCE_MAPPER);
    }

    public long getCompanyAttendanceByDateCount(Long companyId, LocalDate date) {
        String sql = """
                SELECT COUNT(*)
                FROM attendance a
                WHERE a.company_id = :companyId
                  AND a.attendance_date = :attendanceDate
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("attendanceDate", date);
        Long total = jdbcAccess.findLongNamed(sql, params);
        return total != null ? total : 0L;
    }

    public List<AttendancePunchRow> getAttendancePunchesByAttendanceIds(Collection<Long> attendanceIds) {
        if (attendanceIds == null || attendanceIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT p.id,
                       p.attendance_id,
                       p.punch_time,
                       p.punch_type,
                       p.source
                FROM attendance_punches p
                WHERE p.attendance_id IN (:attendanceIds)
                ORDER BY p.attendance_id ASC, p.punch_time ASC, p.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("attendanceIds", attendanceIds);
        return jdbcAccess.findUsingNamedParameters(sql, params, PUNCH_MAPPER);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
