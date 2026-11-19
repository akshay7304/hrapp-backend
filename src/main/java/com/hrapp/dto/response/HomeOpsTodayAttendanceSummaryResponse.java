package com.hrapp.dto.response;

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
public class HomeOpsTodayAttendanceSummaryResponse {

    private Integer present;
    private Integer absent;
    private Integer halfDay;
    private Integer onLeave;
    private Integer weekOff;
    private Integer holiday;
    /** Currently on premises: check-in set, check-out still null. */
    private Integer checkedInNow;
    /** Active employees with no check-in today. */
    private Integer notCheckedIn;
}
