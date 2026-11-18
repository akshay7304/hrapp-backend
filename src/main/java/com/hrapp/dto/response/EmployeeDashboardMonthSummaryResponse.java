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
public class EmployeeDashboardMonthSummaryResponse {

    private Integer month;
    private Integer year;
    private Integer presentDays;
    private Integer absentDays;
    private Integer halfDays;
    private Integer onLeaveDays;
    private Integer weekOffDays;
    private Integer holidayDays;
}
