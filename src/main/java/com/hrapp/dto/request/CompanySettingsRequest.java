package com.hrapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettingsRequest {

    @NotNull(message = "Shift start time is required")
    private LocalTime shiftStartTime;

    @NotNull(message = "Shift end time is required")
    private LocalTime shiftEndTime;

    @NotNull(message = "Shift hours is required")
    @DecimalMin(value = "1.0", message = "Shift hours must be at least 1.0")
    private BigDecimal shiftHours;

    @NotNull(message = "Half day hours is required")
    @DecimalMin(value = "0.5", message = "Half day hours must be at least 0.5")
    private BigDecimal halfDayHours;

    @NotNull(message = "Overtime threshold is required")
    @DecimalMin(value = "1.0", message = "Overtime threshold must be at least 1.0")
    private BigDecimal overtimeAfterHours;

    @NotNull(message = "Late mark minutes is required")
    @Min(value = 0, message = "Late mark minutes cannot be negative")
    private Integer lateMarkAfterMinutes;

    @NotBlank(message = "Week off day is required")
    private String weekOffDay;

    /** Optional — manufacturer/protocol hint for the biometric device. */
    private String deviceBrand;

    /** Optional — shared secret the device must send on every push. */
    private String deviceSecret;
}
