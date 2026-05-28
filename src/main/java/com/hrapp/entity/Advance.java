package com.hrapp.entity;

import com.hrapp.enums.AdvanceStatus;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "advances", indexes = {
        @Index(name = "idx_advance_recovery", columnList = "user_id, status, is_recovered")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Advance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING'")
    @Builder.Default
    private AdvanceStatus status = AdvanceStatus.PENDING;

    @Column(name = "deduct_from_month", columnDefinition = "TINYINT")
    private Integer deductFromMonth;

    @Column(name = "deduct_from_year", columnDefinition = "SMALLINT")
    private Integer deductFromYear;

    @Column(name = "is_recovered", nullable = false)
    @Builder.Default
    private Boolean isRecovered = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "actioned_at")
    private LocalDateTime actionedAt;
}
