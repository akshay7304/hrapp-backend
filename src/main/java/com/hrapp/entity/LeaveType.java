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

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "annual_quota", nullable = false)
    @Builder.Default
    private Integer annualQuota = 0;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private Boolean isPaid = true;

    @Column(name = "allow_half_day", nullable = false)
    @Builder.Default
    private Boolean allowHalfDay = false;

    @Column(name = "carry_forward", nullable = false)
    @Builder.Default
    private Boolean carryForward = false;

    @Column(name = "max_carry_forward_days", nullable = false)
    @Builder.Default
    private Integer maxCarryForwardDays = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
