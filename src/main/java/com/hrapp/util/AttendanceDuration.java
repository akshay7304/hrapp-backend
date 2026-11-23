package com.hrapp.util;

import com.hrapp.entity.AttendancePunch;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.PunchType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Exact attendance durations. Punch timestamps are the source of truth:
 * a span of 8h 29m 57s is stored as 30597 seconds and shown as {@code 08:29:57}.
 * Seconds are never dropped, and the clock string is never derived from a
 * rounded decimal-hour value.
 */
public final class AttendanceDuration {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private AttendanceDuration() {
    }

    public static long between(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0L;
        }
        long seconds = ChronoUnit.SECONDS.between(from, to);
        return Math.max(seconds, 0L);
    }

    /**
     * Sum of completed IN→OUT pairs. An open IN (still checked in) is excluded,
     * and the gap between an OUT and the next IN is excluded.
     */
    public static long sumPairedSeconds(List<AttendancePunch> punches) {
        if (punches == null || punches.isEmpty()) {
            return 0L;
        }
        List<AttendancePunch> sorted = punches.stream()
                .sorted(Comparator.comparing(AttendancePunch::getPunchTime))
                .toList();

        long total = 0L;
        LocalDateTime openIn = null;
        for (AttendancePunch punch : sorted) {
            if (punch.getPunchType() == PunchType.IN) {
                openIn = punch.getPunchTime();
            } else if (punch.getPunchType() == PunchType.OUT && openIn != null) {
                total += between(openIn, punch.getPunchTime());
                openIn = null;
            }
        }
        return total;
    }

    public static long overtimeSeconds(long workedSeconds, BigDecimal thresholdHours) {
        if (thresholdHours == null) {
            return 0L;
        }
        long threshold = hoursToSeconds(thresholdHours);
        return workedSeconds > threshold ? workedSeconds - threshold : 0L;
    }

    public static AttendanceStatus deriveStatus(long workedSeconds, BigDecimal shiftHours, BigDecimal halfDayHours) {
        if (shiftHours != null && workedSeconds >= hoursToSeconds(shiftHours)) {
            return AttendanceStatus.PRESENT;
        }
        if (halfDayHours != null && workedSeconds >= hoursToSeconds(halfDayHours)) {
            return AttendanceStatus.HALF_DAY;
        }
        return AttendanceStatus.ABSENT;
    }

    /**
     * Payroll still sums a decimal-hour snapshot. This is derived from the
     * exact second count and is not what the attendance screen displays.
     */
    public static BigDecimal toPayrollHours(long seconds) {
        if (seconds <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    public static String format(long seconds) {
        long safe = Math.max(seconds, 0L);
        long hours = safe / 3600;
        long minutes = (safe % 3600) / 60;
        long secs = safe % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static long hoursToSeconds(BigDecimal hours) {
        return hours.multiply(SECONDS_PER_HOUR)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
