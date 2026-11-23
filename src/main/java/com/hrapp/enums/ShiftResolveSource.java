package com.hrapp.enums;

/**
 * Where {@code resolveShiftForDate} got the shift from.
 */
public enum ShiftResolveSource {
    /** Explicit employee assignment effective on that date. */
    ASSIGNMENT,
    /** Company default active shift (no personal assignment yet). */
    COMPANY_DEFAULT,
    /** Legacy company_settings times (interim until Phase 5). */
    COMPANY_SETTINGS,
    /** Nothing configured. */
    NONE
}
