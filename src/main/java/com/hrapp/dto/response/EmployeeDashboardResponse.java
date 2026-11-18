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
public class EmployeeDashboardResponse {

    private EmployeeDashboardUserResponse user;
    private AttendanceResponse todayAttendance;
    private EmployeeDashboardShiftResponse shift;
    private EmployeeDashboardMonthSummaryResponse monthSummary;
    private List<LeaveBalanceResponse> leaveBalances;
    private Long pendingLeavesCount;
    private Long pendingAdvancesCount;
    private HolidayResponse nextHoliday;
}
