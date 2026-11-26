package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Adjustment row for payroll list attachment. */
public record SalaryAdjustmentRow(
        Long id,
        Long userId,
        String fullName,
        Integer month,
        Integer year,
        String type,
        BigDecimal amount,
        String reason,
        String createdByName,
        LocalDateTime createdAt
) {
}
