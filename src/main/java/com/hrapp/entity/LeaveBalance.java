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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
        @UniqueConstraint(name = "uq_balance", columnNames = {"user_id", "leave_type_id", "year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false, columnDefinition = "YEAR")
    private Integer year;

    @Column(name = "total_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal totalDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "remaining_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal remainingDays = BigDecimal.ZERO;
}
