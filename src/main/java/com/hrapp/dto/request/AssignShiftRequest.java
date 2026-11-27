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
     * Date from which this shift applies (open-ended until a later assign).
     * Manual assign also clears any later {@code effectiveFrom} rows for this user.
     * Same (user, effectiveFrom) upserts.
     */
    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;
}
