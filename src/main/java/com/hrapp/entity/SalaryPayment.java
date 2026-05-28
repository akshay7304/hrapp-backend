package com.hrapp.entity;

import com.hrapp.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_salary", columnNames = {"user_id", "month", "year"})
        },
        indexes = {
                @Index(name = "idx_salpay_user_month", columnList = "user_id, year, month")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_structure_id", nullable = false)
    private SalaryStructure salaryStructure;

    @Column(name = "month", nullable = false, columnDefinition = "TINYINT")
    private Integer month;

    @Column(name = "year", nullable = false, columnDefinition = "SMALLINT")
    private Integer year;

    @Column(name = "working_days", nullable = false)
    private Integer workingDays;

    @Column(name = "present_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal presentDays = BigDecimal.ZERO;

    @Column(name = "absent_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal absentDays = BigDecimal.ZERO;

    @Column(name = "half_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal halfDays = BigDecimal.ZERO;

    @Column(name = "paid_leave_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal paidLeaveDays = BigDecimal.ZERO;

    @Column(name = "unpaid_leave_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_pay", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(name = "gross_salary", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "advance_deduction", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal advanceDeduction = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('DRAFT','PAID') DEFAULT 'DRAFT'")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.DRAFT;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "payslip_url", length = 255)
    private String payslipUrl;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
