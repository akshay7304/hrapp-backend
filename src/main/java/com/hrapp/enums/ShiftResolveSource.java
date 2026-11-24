package com.hrapp.enums;

/**
 * Where {@code resolveShiftForDate} got the shift from.
 * Resolve order: ASSIGNMENT → COMPANY_DEFAULT → NONE.
 * {@link #COMPANY_SETTINGS} is retained for API compatibility but is never emitted.
 */
public enum ShiftResolveSource {
    /** Explicit employee assignment effective on that date. */
    ASSIGNMENT,
    /** Company default active shift (no personal assignment yet). */
    COMPANY_DEFAULT,
    /** @deprecated Unused since Phase 5 — settings are not a shift source. */
    @Deprecated
    COMPANY_SETTINGS,
    /** No assignment and no company default shift. */
    NONE
}
