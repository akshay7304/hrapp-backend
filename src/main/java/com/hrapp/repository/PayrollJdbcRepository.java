package com.hrapp.repository;

import com.hrapp.jdbc.JdbcAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payroll list reads with user / salary-structure / generator joins.
 * Paging runs in Postgres ({@code LIMIT}/{@code OFFSET} + matching {@code COUNT}).
 */
@Repository
@RequiredArgsConstructor
public class PayrollJdbcRepository {

    private static final String PAYMENT_SELECT = """
            SELECT sp.id,
                   sp.user_id,
                   u.full_name,
                   u.emp_code,
                   u.salary_type,
                   sp.month,
                   sp.year,
                   sp.working_days,
                   sp.present_days,
                   sp.absent_days,
                   sp.half_days,
                   sp.paid_leave_days,
                   sp.unpaid_leave_days,
                   sp.overtime_hours,
                   sp.overtime_pay,
                   ss.basic,
                   ss.hra,
                   ss.allowances,
                   ss.pf_deduction,
                   ss.other_deductions,
                   sp.gross_salary,
                   sp.advance_deduction,
                   sp.total_deductions,
                   sp.net_salary,
                   sp.status,
                   sp.paid_on,
                   sp.remarks,
                   gb.full_name AS generated_by_name,
                   sp.created_at
            FROM salary_payments sp
            JOIN users u ON u.id = sp.user_id
            JOIN salary_structures ss ON ss.id = sp.salary_structure_id
            LEFT JOIN users gb ON gb.id = sp.generated_by
            """;

    private static final RowMapper<SalaryPaymentRow> PAYMENT_MAPPER = (rs, rowNum) -> new SalaryPaymentRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("emp_code"),
            rs.getString("salary_type"),
            rs.getInt("month"),
            rs.getInt("year"),
            rs.getInt("working_days"),
            rs.getBigDecimal("present_days"),
            rs.getBigDecimal("absent_days"),
            rs.getBigDecimal("half_days"),
            rs.getBigDecimal("paid_leave_days"),
            rs.getBigDecimal("unpaid_leave_days"),
            rs.getBigDecimal("overtime_hours"),
            rs.getBigDecimal("overtime_pay"),
            rs.getBigDecimal("basic"),
            rs.getBigDecimal("hra"),
            rs.getBigDecimal("allowances"),
            rs.getBigDecimal("pf_deduction"),
            rs.getBigDecimal("other_deductions"),
            rs.getBigDecimal("gross_salary"),
            rs.getBigDecimal("advance_deduction"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("net_salary"),
            rs.getString("status"),
            rs.getObject("paid_on", LocalDate.class),
            rs.getString("remarks"),
            rs.getString("generated_by_name"),
            rs.getObject("created_at", LocalDateTime.class));

    private static final RowMapper<SalaryAdjustmentRow> ADJUSTMENT_MAPPER = (rs, rowNum) -> new SalaryAdjustmentRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getInt("month"),
            rs.getInt("year"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getString("reason"),
            rs.getString("created_by_name"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcAccess jdbcAccess;

    public List<SalaryPaymentRow> getPayrollByCompanyMonthYear(
            Long companyId, Integer month, Integer year, int size, long offset) {
        String sql = PAYMENT_SELECT
                + """
                  WHERE u.company_id = :companyId
                    AND sp.month = :month
                    AND sp.year = :year
                  ORDER BY u.full_name ASC, sp.id ASC
                  LIMIT :size OFFSET :offset
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("month", month)
                .addValue("year", year)
                .addValue("size", size)
                .addValue("offset", offset);
        return jdbcAccess.findUsingNamedParameters(sql, params, PAYMENT_MAPPER);
    }

    public long getPayrollByCompanyMonthYearCount(Long companyId, Integer month, Integer year) {
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

    public List<SalaryAdjustmentRow> getAdjustmentsByCompanyMonthYear(
            Long companyId, Integer month, Integer year) {
        String sql = """
                SELECT sa.id,
                       sa.user_id,
                       u.full_name,
                       sa.month,
                       sa.year,
                       sa.type,
                       sa.amount,
                       sa.reason,
                       cb.full_name AS created_by_name,
                       sa.created_at
                FROM salary_adjustments sa
                JOIN users u ON u.id = sa.user_id
                LEFT JOIN users cb ON cb.id = sa.created_by
                WHERE sa.company_id = :companyId
                  AND sa.month = :month
                  AND sa.year = :year
                ORDER BY sa.created_at ASC, sa.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("month", month)
                .addValue("year", year);
        return jdbcAccess.findUsingNamedParameters(sql, params, ADJUSTMENT_MAPPER);
    }
}
