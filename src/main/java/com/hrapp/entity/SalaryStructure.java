package com.hrapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_structures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "basic", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal basic = BigDecimal.ZERO;

    @Column(name = "hra", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(name = "allowances", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "pf_deduction", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal pfDeduction = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "overtime_rate", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal overtimeRate = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
