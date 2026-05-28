package com.hrapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class SalaryStructureRequest {

    @NotNull(message = "User is required")
    private Long userId;

    @NotNull(message = "Basic is required")
    @DecimalMin(value = "0.0", message = "Basic cannot be negative")
    private BigDecimal basic;

    @NotNull(message = "HRA is required")
    @DecimalMin(value = "0.0", message = "HRA cannot be negative")
    private BigDecimal hra;

    @NotNull(message = "Allowances are required")
    @DecimalMin(value = "0.0", message = "Allowances cannot be negative")
    private BigDecimal allowances;

    @NotNull(message = "PF deduction is required")
    @DecimalMin(value = "0.0", message = "PF deduction cannot be negative")
    private BigDecimal pfDeduction;

    @NotNull(message = "Other deductions are required")
    @DecimalMin(value = "0.0", message = "Other deductions cannot be negative")
    private BigDecimal otherDeductions;

    @NotNull(message = "Overtime rate is required")
    @DecimalMin(value = "0.0", message = "Overtime rate cannot be negative")
    private BigDecimal overtimeRate;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;
}
