package com.hrapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Attendance day fields needed to build the monthly attendance report. */
public record AttendanceReportDayRow(
        Long userId,
        LocalDate attendanceDate,
        LocalDateTime checkOut,
        String status,
        BigDecimal overtimeHours
) {
}
