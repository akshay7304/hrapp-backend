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
public class HomeOpsResponse {

    private HomeOpsCompanyResponse company;
    private HomeOpsHeadcountResponse headcount;
    private Long pendingLeavesCount;
    private Long pendingAdvancesCount;
    private Long pendingThumbLogsCount;
    private HomeOpsTodayAttendanceSummaryResponse todayAttendanceSummary;
    private HomeOpsPayrollStatusResponse thisMonthPayrollStatus;
}
