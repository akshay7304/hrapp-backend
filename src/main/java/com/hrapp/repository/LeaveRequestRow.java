package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Leave request list row with user, leave type, and actioner names joined. */
public record LeaveRequestRow(
        Long id,
        Long userId,
        String fullName,
        String empCode,
        Long leaveTypeId,
        String leaveTypeName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalDays,
        Boolean isHalfDay,
        String halfDayType,
        String reason,
        String status,
        String rejectReason,
        String actionedByName,
        LocalDateTime appliedAt,
        LocalDateTime actionedAt
) {
}
