package com.hrapp.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeRequest {

    @NotBlank(message = "Leave type name is required")
    private String name;

    @NotNull(message = "Annual quota is required")
    @Min(value = 0, message = "Annual quota cannot be negative")
    private Integer annualQuota;

    @NotNull(message = "Paid/unpaid flag is required")
    private Boolean isPaid;

    /** When null on create/update, defaults to {@code false}. */
    private Boolean allowHalfDay;

    /** When null on create/update, defaults to {@code false}. */
    private Boolean carryForward;

    /**
     * Max days that may carry into the next cycle. Ignored / forced to 0 when
     * {@code carryForward} is false. Null defaults to 0.
     */
    @Min(value = 0, message = "Max carry-forward days cannot be negative")
    private Integer maxCarryForwardDays;
}
