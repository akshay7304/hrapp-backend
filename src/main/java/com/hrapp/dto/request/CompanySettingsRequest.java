package com.hrapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Company operational settings. Week-off and leave counting are authoritative.
 * Shift start/end/hours/late/OT fields are legacy display-only after Phase 5 —
 * attendance uses the shift master / assignments. Send them optionally to keep
 * existing FE payloads working; omit to leave unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettingsRequest {

    /** Legacy — not used for attendance resolve after Phase 5. */
    private LocalTime shiftStartTime;

    /** Legacy — not used for attendance resolve after Phase 5. */
    private LocalTime shiftEndTime;

    /** Legacy — not used for attendance thresholds after Phase 5. */
    @DecimalMin(value = "1.0", message = "Shift hours must be at least 1.0")
    private BigDecimal shiftHours;

    /** Legacy — not used for attendance thresholds after Phase 5. */
    @DecimalMin(value = "0.5", message = "Half day hours must be at least 0.5")
    private BigDecimal halfDayHours;

    /** Legacy — OT is manual-only; threshold not applied automatically. */
    @DecimalMin(value = "1.0", message = "Overtime threshold must be at least 1.0")
    private BigDecimal overtimeAfterHours;

    /** Legacy — late uses shift graceLateMinutes. */
    @Min(value = 0, message = "Late mark minutes cannot be negative")
    private Integer lateMarkAfterMinutes;

    /**
     * One or more week-off days, e.g. {@code ["SATURDAY","SUNDAY"]}.
     * Preferred field for the web app.
     */
    private List<String> weekOffDays;

    /**
     * Legacy single-day field. Used only when {@link #weekOffDays} is null/empty.
     */
    private String weekOffDay;

    /**
     * Optional leave-day counting mode:
     * {@code EXCLUDE_WEEK_OFF_AND_HOLIDAYS}, {@code EXCLUDE_WEEK_OFF}, {@code CALENDAR_DAYS}.
     */
    private String leaveCountType;

    /** Optional — manufacturer/protocol hint for the biometric device. */
    private String deviceBrand;

    /**
     * Optional — device SN / serial for biometric routing.
     * Blank clears the serial (device pushes will fail until set again).
     */
    private String deviceSerial;

    /**
     * Optional — shared secret the device must send on every push.
     * Write-only: never returned on GET. Omit to leave unchanged.
     */
    private String deviceSecret;
}
