package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeResponse {

    private Long id;
    private String name;
    private Integer annualQuota;
    private Boolean isPaid;
    private Boolean allowHalfDay;
    private Boolean carryForward;
    private Integer maxCarryForwardDays;
    private Long companyId;
    private LocalDateTime createdAt;
}
