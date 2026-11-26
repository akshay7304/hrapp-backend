package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Advance list row with requester and approver names joined. */
public record AdvanceRow(
        Long id,
        Long userId,
        String fullName,
        String empCode,
        BigDecimal amount,
        String reason,
        String rejectReason,
        String status,
        Integer deductFromMonth,
        Integer deductFromYear,
        Boolean isRecovered,
        String approvedByName,
        LocalDateTime createdAt,
        LocalDateTime actionedAt
) {
}
