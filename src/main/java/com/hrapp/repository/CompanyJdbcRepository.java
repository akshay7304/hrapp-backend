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
 * Company list reads with employee count. Paging runs in Postgres
 * ({@code LIMIT}/{@code OFFSET} + matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class CompanyJdbcRepository {

    private static final String COMPANY_SELECT = """
            SELECT c.id,
                   c.name,
                   c.address,
                   c.logo_url,
                   c.is_active,
                   COALESCE(emp.total_employees, 0) AS total_employees,
                   c.created_at
            FROM companies c
            LEFT JOIN LATERAL (
                SELECT COUNT(*)::int AS total_employees
                FROM users u
                WHERE u.company_id = c.id
            ) emp ON TRUE
            """;

    private static final RowMapper<CompanyRow> ROW_MAPPER = (rs, rowNum) -> new CompanyRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("logo_url"),
            nullableBoolean(rs, "is_active"),
            rs.getInt("total_employees"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcAccess jdbcAccess;

    public List<CompanyRow> getAllCompanies(int size, long offset) {
        String sql = COMPANY_SELECT
                + " ORDER BY c.created_at DESC, c.id DESC LIMIT :size OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, ROW_MAPPER);
    }

    public long getAllCompaniesCount() {
        Long total = jdbcAccess.findLongNamed(
                "SELECT COUNT(*) FROM companies", new MapSqlParameterSource());
        return total != null ? total : 0L;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
