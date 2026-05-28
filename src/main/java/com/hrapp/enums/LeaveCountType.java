package com.hrapp.enums;

/**
 * Drives how the leave-day total is computed between {@code fromDate} and
 * {@code toDate}. Stored as a String in {@code company_settings.leave_count_type}.
 */
public enum LeaveCountType {
    CALENDAR_DAYS,
    EXCLUDE_WEEK_OFF,
    EXCLUDE_WEEK_OFF_AND_HOLIDAYS
}
