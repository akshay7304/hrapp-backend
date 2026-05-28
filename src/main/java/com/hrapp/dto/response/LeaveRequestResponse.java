package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String empCode;
    private Long leaveTypeId;
    private String leaveTypeName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal totalDays;
    private Boolean isHalfDay;
    private String halfDayType;
    private String reason;
    private String status;
    private String rejectReason;
    private String actionedByName;
    private LocalDateTime appliedAt;
    private LocalDateTime actionedAt;
}
