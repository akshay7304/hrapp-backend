package com.hrapp.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One attendance list/history row with user identity joined in the same query.
 */
public record AttendanceRow(
        Long id,
        Long userId,
        String fullName,
        String empCode,
        Long companyId,
        LocalDate attendanceDate,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        Long workedSeconds,
        Long overtimeSeconds,
        String status,
        String source,
        Boolean isManual,
        Boolean isAutoCheckout,
        Boolean isLate,
        String manualReason
) {
}
