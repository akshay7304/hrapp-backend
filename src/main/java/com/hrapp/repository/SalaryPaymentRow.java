package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Payroll list row: payment + user + salary structure + generator name. */
public record SalaryPaymentRow(
        Long id,
        Long userId,
        String fullName,
        String empCode,
        String salaryType,
        Integer month,
        Integer year,
        Integer workingDays,
        BigDecimal presentDays,
        BigDecimal absentDays,
        BigDecimal halfDays,
        BigDecimal paidLeaveDays,
        BigDecimal unpaidLeaveDays,
        BigDecimal overtimeHours,
        BigDecimal overtimePay,
        BigDecimal basic,
        BigDecimal hra,
        BigDecimal allowances,
        BigDecimal pfDeduction,
        BigDecimal otherDeductions,
        BigDecimal grossSalary,
        BigDecimal advanceDeduction,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        String status,
        LocalDate paidOn,
        String remarks,
        String generatedByName,
        LocalDateTime createdAt
) {
}
