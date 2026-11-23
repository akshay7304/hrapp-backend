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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shifts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_shift_company_name", columnNames = {"company_id", "name"})
        },
        indexes = {
                @Index(name = "idx_shifts_company_active", columnList = "company_id, is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_overnight", nullable = false)
    @Builder.Default
    private Boolean isOvernight = false;

    @Column(name = "grace_late_minutes", nullable = false)
    @Builder.Default
    private Integer graceLateMinutes = 0;

    @Column(name = "full_day_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal fullDayHours;

    @Column(name = "half_day_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal halfDayHours;

    @Column(name = "break_minutes", nullable = false)
    @Builder.Default
    private Integer breakMinutes = 0;

    @Column(name = "break_paid", nullable = false)
    @Builder.Default
    private Boolean breakPaid = false;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
