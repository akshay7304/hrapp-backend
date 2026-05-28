package com.hrapp.dto.request;

import com.hrapp.enums.AdvanceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class ActionAdvanceRequest {

    /** Must be either {@code APPROVED} or {@code REJECTED} — validated in the service. */
    @NotNull(message = "Status is required")
    private AdvanceStatus status;

    /** Required when {@code status = APPROVED}. */
    @Min(value = 1, message = "Deduct from month must be between 1 and 12")
    @Max(value = 12, message = "Deduct from month must be between 1 and 12")
    private Integer deductFromMonth;

    /** Required when {@code status = APPROVED}. */
    private Integer deductFromYear;

    /** Required when {@code status = REJECTED}. */
    private String rejectReason;
}
