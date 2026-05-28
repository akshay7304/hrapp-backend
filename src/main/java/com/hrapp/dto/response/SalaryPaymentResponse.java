package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryPaymentResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String empCode;

    private Integer month;
    private Integer year;
    private String salaryType;

    private Integer workingDays;
    private BigDecimal presentDays;
    private BigDecimal absentDays;
    private BigDecimal halfDays;
    private BigDecimal paidLeaveDays;
    private BigDecimal unpaidLeaveDays;
    private BigDecimal overtimeHours;
    private BigDecimal overtimePay;

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
    private String remarks;

    private List<SalaryAdjustmentResponse> adjustments;
    private String generatedByName;
    private LocalDateTime createdAt;
}
