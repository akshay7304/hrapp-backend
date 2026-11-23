package com.hrapp.dto.request;

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
public class AssignShiftRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "shiftId is required")
    private Long shiftId;

    /**
     * Date from which this shift applies. Past attendance for earlier dates
     * keeps the previous assignment. Same (user, effectiveFrom) upserts.
     */
    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;
}
