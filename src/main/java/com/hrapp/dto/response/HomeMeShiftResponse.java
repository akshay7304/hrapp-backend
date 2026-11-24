package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeMeShiftResponse {

    private Long shiftId;
    private String shiftName;
    private String source;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer graceLateMinutes;
}
