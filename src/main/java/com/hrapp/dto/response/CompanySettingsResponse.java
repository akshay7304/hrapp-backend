package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettingsResponse {

    private Long id;
    private Long companyId;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private BigDecimal shiftHours;
    private BigDecimal halfDayHours;
    private BigDecimal overtimeAfterHours;
    private Integer lateMarkAfterMinutes;
    /** Preferred: all week-off days, e.g. {@code ["SATURDAY","SUNDAY"]}. */
    private List<String> weekOffDays;
    /** Legacy: first week-off day (same as {@code weekOffDays[0]} when present). */
    private String weekOffDay;
    private String deviceBrand;
    /** Device SN / id used for biometric routing. Not the enrol employee id. */
    private String deviceSerial;
    /** True when a device shared secret is configured (secret itself is never returned). */
    private Boolean deviceSecretConfigured;
    private LocalDateTime updatedAt;
}
