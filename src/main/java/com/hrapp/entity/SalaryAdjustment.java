package com.hrapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import java.time.LocalDateTime;

/**
 * One-off additions or deductions applied to a single month's payroll for an
 * employee (e.g. bonus, penalty, reimbursement). Stored separately from
 * {@link SalaryPayment} so the audit trail survives payroll re-runs.
 *
 * <p>{@code type} is a free-form String at the DB layer matching either
 * {@code "ADDITION"} or {@code "DEDUCTION"} — validated in the service.
 */
@Entity
@Table(name = "salary_adjustments", indexes = {
        @Index(name = "idx_adjustment_user_month", columnList = "user_id, year, month"),
        @Index(name = "idx_adjustment_company_month", columnList = "company_id, year, month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "month", nullable = false, columnDefinition = "TINYINT")
    private Integer month;

    @Column(name = "year", nullable = false, columnDefinition = "SMALLINT")
    private Integer year;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
