package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeMeResponse {

    private HomeMeUserResponse user;
    private AttendanceResponse todayAttendance;
    private HomeMeShiftResponse shift;
    private HomeMeMonthSummaryResponse monthSummary;
    private List<LeaveBalanceResponse> leaveBalances;
    private Long pendingLeavesCount;
    private Long pendingAdvancesCount;
    private HolidayResponse nextHoliday;
}
