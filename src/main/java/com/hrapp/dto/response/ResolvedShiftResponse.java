package com.hrapp.dto.response;

import com.hrapp.enums.ShiftResolveSource;
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
public class ResolvedShiftResponse {

    private Long userId;
    private LocalDate date;
    private ShiftResolveSource source;
    /** Present unless {@code source == NONE}. */
    private ShiftResponse shift;
    private Long assignmentId;
    private LocalDate assignmentEffectiveFrom;
}
