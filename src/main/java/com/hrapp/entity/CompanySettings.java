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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Per-company attendance / payroll configuration. Exactly one row per company
 * (enforced by the unique key on {@code company_id}).
 */
@Entity
@Table(name = "company_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_settings_company", columnNames = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    @Column(name = "shift_hours", precision = 4, scale = 2)
    private BigDecimal shiftHours;

    @Column(name = "half_day_hours", precision = 4, scale = 2)
    private BigDecimal halfDayHours;

    @Column(name = "overtime_after_hours", precision = 4, scale = 2)
    private BigDecimal overtimeAfterHours;

    @Column(name = "late_mark_after_minutes")
    private Integer lateMarkAfterMinutes;

    @Column(name = "week_off_day", length = 20)
    private String weekOffDay;

    /**
     * Controls how leave-day counts are computed. Stored as plain text so HR can
     * extend it later without an enum change. See {@link com.hrapp.enums.LeaveCountType}
     * for the supported values; default is {@code EXCLUDE_WEEK_OFF_AND_HOLIDAYS}.
     */
    @Column(name = "leave_count_type", nullable = false, length = 50)
    @Builder.Default
    private String leaveCountType = "EXCLUDE_WEEK_OFF_AND_HOLIDAYS";

    /**
     * Manufacturer / protocol identifier of the biometric device pushing logs
     * for this company (e.g. {@code ZKTECO}, {@code ESSL}, {@code GENERIC}).
     * Used by ops as a routing hint; the actual endpoint chosen by the device
     * is what determines parsing.
     */
    @Column(name = "device_brand", length = 50)
    private String deviceBrand;

    /**
     * Shared secret the device must include in every push so the server can
     * verify the call came from the registered device. Stored as plain text
     * because the device fleet doesn't support TLS client auth.
     */
    @Column(name = "device_secret", length = 100)
    private String deviceSecret;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
