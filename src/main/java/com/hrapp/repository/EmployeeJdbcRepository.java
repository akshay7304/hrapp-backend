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
import java.util.Locale;
import java.util.Optional;

/**
 * Company employee reads that join lookup tables. Paging and filters run in
 * Postgres ({@code LIMIT}/{@code OFFSET} plus a matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class EmployeeJdbcRepository {

    private static final String EMPLOYEE_SELECT = """
            SELECT u.id,
                   u.emp_code,
                   u.full_name,
                   u.mobile,
                   u.email,
                   u.salary_type,
                   u.joining_date,
                   u.department_id,
                   d.name AS department_name,
                   u.designation_id,
                   des.name AS designation_name,
                   u.status_id,
                   st.name AS status_name,
                   u.company_id,
                   u.profile_pic_url,
                   u.thumb_device_id,
                   u.created_at,
                   COALESCE(roles.role_names, '') AS role_names
            FROM users u
            LEFT JOIN departments d ON d.id = u.department_id
            LEFT JOIN designations des ON des.id = u.designation_id
            JOIN user_statuses st ON st.id = u.status_id
            LEFT JOIN LATERAL (
                SELECT string_agg(r.name, ',' ORDER BY r.name) AS role_names
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id
            ) roles ON TRUE
            """;

    private static final String COUNT_FROM = "SELECT COUNT(*) FROM users u";

    private static final RowMapper<EmployeeRow> ROW_MAPPER = (rs, rowNum) -> new EmployeeRow(
            rs.getLong("id"),
            rs.getString("emp_code"),
            rs.getString("full_name"),
            rs.getString("mobile"),
            rs.getString("email"),
            rs.getString("salary_type"),
            rs.getObject("joining_date", LocalDate.class),
            nullableLong(rs, "department_id"),
            rs.getString("department_name"),
            nullableLong(rs, "designation_id"),
            rs.getString("designation_name"),
            nullableLong(rs, "status_id"),
            rs.getString("status_name"),
            nullableLong(rs, "company_id"),
            rs.getString("profile_pic_url"),
            rs.getString("thumb_device_id"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getString("role_names"));

    private final JdbcAccess jdbcAccess;

    public List<EmployeeRow> getAllEmployees(
            Long companyId, Long departmentId, Long statusId, String search, int size, long offset) {
        EmployeeListQuery query = buildEmployeeListQuery(companyId, departmentId, statusId, search);
        String sql = EMPLOYEE_SELECT
                + query.where()
                + " ORDER BY u.full_name ASC, u.id ASC LIMIT :size OFFSET :offset";
        query.params().addValue("size", size).addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, query.params(), ROW_MAPPER);
    }

    public long getAllEmployeesCount(Long companyId, Long departmentId, Long statusId, String search) {
        EmployeeListQuery query = buildEmployeeListQuery(companyId, departmentId, statusId, search);
        Long total = jdbcAccess.findLongNamed(COUNT_FROM + query.where(), query.params());
        return total != null ? total : 0L;
    }

    public Optional<EmployeeRow> getEmployeeById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<EmployeeRow> rows = jdbcAccess.findUsingNamedParameters(
                EMPLOYEE_SELECT + " WHERE u.id = :id", params, ROW_MAPPER);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * Next {@code EMPnnn} sequence for the company: one greater than the
     * highest existing numeric code, or {@code 1} when none exist.
     */
    public long getNextEmployeeCodeSequence(Long companyId) {
        String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(emp_code FROM 4) AS BIGINT)), 0) + 1
                FROM users
                WHERE company_id = :companyId
                  AND emp_code ~ '^EMP[0-9]+$'
                """;
        Long next = jdbcAccess.findLongNamed(sql, new MapSqlParameterSource("companyId", companyId));
        return next != null && next > 0 ? next : 1L;
    }

    private static EmployeeListQuery buildEmployeeListQuery(
            Long companyId, Long departmentId, Long statusId, String search) {
        StringBuilder where = new StringBuilder(" WHERE u.company_id = :companyId");
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId);
        if (departmentId != null) {
            where.append(" AND u.department_id = :departmentId");
            params.addValue("departmentId", departmentId);
        }
        if (statusId != null) {
            where.append(" AND u.status_id = :statusId");
            params.addValue("statusId", statusId);
        }
        if (search != null && !search.isBlank()) {
            where.append("""
                     AND (strpos(lower(u.full_name), :search) > 0
                       OR strpos(lower(COALESCE(u.emp_code, '')), :search) > 0
                       OR strpos(u.mobile, :search) > 0)
                    """);
            params.addValue("search", search.trim().toLowerCase(Locale.ROOT));
        }
        return new EmployeeListQuery(where.toString(), params);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record EmployeeListQuery(String where, MapSqlParameterSource params) {
    }
}
