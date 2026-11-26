package com.hrapp.repository;

import java.time.LocalDateTime;

/** Punch row for attendance list/detail mapping without JPA. */
public record AttendancePunchRow(
        Long id,
        Long attendanceId,
        LocalDateTime punchTime,
        String punchType,
        String source
) {
}
