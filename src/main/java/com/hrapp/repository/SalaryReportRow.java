package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One salary-report list row: payment + user + structure + dept/designation. */
public record SalaryReportRow(
        Long userId,
        String fullName,
        String empCode,
        String departmentName,
        String designationName,
        Integer month,
        Integer year,
        String salaryType,
        BigDecimal basic,
        BigDecimal hra,
        BigDecimal allowances,
        BigDecimal grossSalary,
        BigDecimal pfDeduction,
        BigDecimal otherDeductions,
        BigDecimal advanceDeduction,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        String status,
        LocalDate paidOn
) {
}
