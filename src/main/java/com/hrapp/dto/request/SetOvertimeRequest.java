package com.hrapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetOvertimeRequest {

    /**
     * Manual overtime in decimal hours (e.g. 1.5 = 1h 30m).
     * Use 0 to clear. Auto OT from worked time is never applied.
     */
    @NotNull(message = "overtimeHours is required")
    @DecimalMin(value = "0.0", message = "overtimeHours cannot be negative")
    private BigDecimal overtimeHours;
}
