package com.hrapp.dto.request;

import com.hrapp.enums.HalfDayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyLeaveRequest {

    @NotNull(message = "Leave type is required")
    private Long leaveTypeId;

    @NotNull(message = "From date is required")
    private LocalDate fromDate;

    @NotNull(message = "To date is required")
    private LocalDate toDate;

    @NotBlank(message = "Reason is required")
    private String reason;

    @Builder.Default
    private Boolean isHalfDay = false;

    /**
     * Required only when {@code isHalfDay = true}; service validates this rule.
     */
    private HalfDayType halfDayType;
}
