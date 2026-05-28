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
public class SalaryStructureResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private BigDecimal basic;
    private BigDecimal hra;
    private BigDecimal allowances;
    private BigDecimal pfDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal overtimeRate;
    private LocalDate effectiveFrom;
    /** Convenience: {@code basic + hra + allowances}. */
    private BigDecimal grossSalary;
    private LocalDateTime createdAt;
}
