package com.hrapp.entity;

import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.AttendanceStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_attendance", columnNames = {"user_id", "attendance_date"})
        },
        indexes = {
                @Index(name = "idx_att_company_date", columnList = "company_id, attendance_date"),
                @Index(name = "idx_att_user_date", columnList = "user_id, attendance_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

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

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in")
    private LocalDateTime checkIn;

    @Column(name = "check_out")
    private LocalDateTime checkOut;

    @Column(name = "worked_hours", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal workedHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('PRESENT','ABSENT','HALF_DAY','ON_LEAVE','HOLIDAY','WEEK_OFF') DEFAULT 'ABSENT'")
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.ABSENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false,
            columnDefinition = "ENUM('THUMB','MOBILE','MANUAL') DEFAULT 'MOBILE'")
    @Builder.Default
    private AttendanceSource source = AttendanceSource.MOBILE;

    @Column(name = "is_manual", nullable = false)
    @Builder.Default
    private Boolean isManual = false;

    @Column(name = "is_auto_checkout", nullable = false)
    @Builder.Default
    private Boolean isAutoCheckout = false;

    @Column(name = "manual_reason", columnDefinition = "TEXT")
    private String manualReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by")
    private User markedBy;
}
