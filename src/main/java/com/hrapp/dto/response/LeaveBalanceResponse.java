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
public class LeaveBalanceResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private Long leaveTypeId;
    private String leaveTypeName;
    private Boolean isPaid;
    private Boolean allowHalfDay;
    private Boolean carryForward;
    private Integer year;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    private BigDecimal remainingDays;
}
