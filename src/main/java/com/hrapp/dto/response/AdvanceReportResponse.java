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
public class AdvanceReportResponse {

    private Long userId;
    private String fullName;
    private String empCode;
    private String departmentName;

    private BigDecimal totalAdvanceRequested;
    private BigDecimal totalAdvanceApproved;
    private BigDecimal totalAdvanceRecovered;
    private BigDecimal totalAdvancePending;

    private List<AdvanceResponse> advances;
}
