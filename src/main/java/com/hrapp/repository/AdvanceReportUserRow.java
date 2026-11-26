package com.hrapp.repository;

import java.math.BigDecimal;

/** Advance-report page row: one employee with aggregate totals. */
public record AdvanceReportUserRow(
        Long userId,
        String fullName,
        String empCode,
        String departmentName,
        BigDecimal totalAdvanceRequested,
        BigDecimal totalAdvanceApproved,
        BigDecimal totalAdvanceRecovered,
        BigDecimal totalAdvancePending
) {
}
