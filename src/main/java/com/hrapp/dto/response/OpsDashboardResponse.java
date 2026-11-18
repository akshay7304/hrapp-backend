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
public class OpsDashboardResponse {

    private OpsDashboardCompanyResponse company;
    private OpsDashboardHeadcountResponse headcount;
    private Long pendingLeavesCount;
    private Long pendingAdvancesCount;
    private Long pendingThumbLogsCount;
    private OpsDashboardTodayAttendanceSummaryResponse todayAttendanceSummary;
    private OpsDashboardPayrollStatusResponse thisMonthPayrollStatus;
}
