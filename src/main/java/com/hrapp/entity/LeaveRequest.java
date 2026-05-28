package com.hrapp.entity;

import com.hrapp.enums.LeaveRequestStatus;
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
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leavereq_user_status", columnList = "user_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

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

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "total_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalDays;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_half_day", nullable = false)
    @Builder.Default
    private Boolean isHalfDay = false;

    /**
     * Stored as plain text matching {@link com.hrapp.enums.HalfDayType} names
     * (e.g. {@code "MORNING"} / {@code "AFTERNOON"}). Null for full-day leaves.
     */
    @Column(name = "half_day_type", length = 20)
    private String halfDayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING'")
    @Builder.Default
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actioned_by")
    private User actionedBy;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "actioned_at")
    private LocalDateTime actionedAt;
}
