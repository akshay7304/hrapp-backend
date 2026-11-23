package com.hrapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ShiftRequest {

    @NotBlank(message = "Shift name is required")
    @Size(max = 80, message = "Shift name must be at most 80 characters")
    private String name;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /** When null, defaults to false. Required true when endTime is before/equal startTime. */
    private Boolean isOvernight;

    @NotNull(message = "Grace late minutes is required")
    @Min(value = 0, message = "Grace late minutes cannot be negative")
    private Integer graceLateMinutes;

    @NotNull(message = "Full day hours is required")
    @DecimalMin(value = "0.01", message = "Full day hours must be greater than 0")
    private BigDecimal fullDayHours;

    @NotNull(message = "Half day hours is required")
    @DecimalMin(value = "0.00", message = "Half day hours cannot be negative")
    private BigDecimal halfDayHours;

    @NotNull(message = "Break minutes is required")
    @Min(value = 0, message = "Break minutes cannot be negative")
    private Integer breakMinutes;

    /** When null, defaults to false. */
    private Boolean breakPaid;

    /** When null, defaults to false. Only one active default per company. */
    private Boolean isDefault;
}
