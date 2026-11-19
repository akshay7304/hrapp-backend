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
public class HomeOpsPayrollStatusResponse {

    private Integer month;
    private Integer year;
    private Long generated;
    private Long paid;
    private Long unpaid;
}
