package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceReportResponse {

    private Long userId;
    private String fullName;
    private String empCode;
    private String departmentName;
    private String designationName;

    private Integer month;
    private Integer year;

    private Integer totalWorkingDays;
    private BigDecimal presentDays;
    private BigDecimal absentDays;
    private BigDecimal halfDays;
    private BigDecimal paidLeaveDays;
    private BigDecimal unpaidLeaveDays;
    private BigDecimal onLeaveDays;
    private Integer holidayDays;
    private Integer weekOffDays;
    private BigDecimal overtimeHours;

    /** {@code (presentDays / totalWorkingDays) * 100}, with 2-decimal precision. Returns 0 when working days is 0. */
    private BigDecimal attendancePercentage;
}
