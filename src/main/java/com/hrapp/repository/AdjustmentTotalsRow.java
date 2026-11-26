package com.hrapp.repository;

import java.math.BigDecimal;

/** Per-user salary adjustment totals for a company month. */
public record AdjustmentTotalsRow(
        Long userId,
        BigDecimal additions,
        BigDecimal deductions
) {
}
