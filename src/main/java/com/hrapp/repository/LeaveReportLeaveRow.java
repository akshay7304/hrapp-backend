package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Leave request row for leave reports, including leave-type paid flag. */
public record LeaveReportLeaveRow(
        Long id,
        Long userId,
        String fullName,
        String empCode,
        Long leaveTypeId,
        String leaveTypeName,
        Boolean leaveTypeIsPaid,
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
