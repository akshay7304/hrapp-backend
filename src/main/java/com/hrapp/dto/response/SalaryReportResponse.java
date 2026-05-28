package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryReportResponse {

    private Long userId;
    private String fullName;
    private String empCode;
    private String departmentName;
    private String designationName;

    private Integer month;
    private Integer year;
    private String salaryType;

    private BigDecimal basic;
    private BigDecimal hra;
    private BigDecimal allowances;
    private BigDecimal grossSalary;

    private BigDecimal pfDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal advanceDeduction;
    private BigDecimal totalAdjustmentAdditions;
    private BigDecimal totalAdjustmentDeductions;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;

    private String status;
    private LocalDate paidOn;
}
