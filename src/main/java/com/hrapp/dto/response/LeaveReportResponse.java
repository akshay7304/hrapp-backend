package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveReportResponse {

    private Long userId;
    private String fullName;
    private String empCode;
    private String departmentName;

    private Integer month;
    private Integer year;

    /** All leave requests for the employee whose {@code fromDate} falls in the requested month, any status. */
    private List<LeaveRequestResponse> leaveRequests;

    private BigDecimal totalLeavesTaken;
    private BigDecimal totalPaidLeaves;
    private BigDecimal totalUnpaidLeaves;
}
