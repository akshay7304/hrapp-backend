package com.hrapp.entity;

import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.PunchType;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_punches",
        indexes = {
                @Index(name = "idx_punch_attendance", columnList = "attendance_id, punch_time"),
                @Index(name = "idx_punch_user_time", columnList = "user_id, punch_time")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendancePunch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false,
            columnDefinition = "ENUM('IN','OUT')")
    private PunchType punchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false,
            columnDefinition = "ENUM('MOBILE','THUMB','MANUAL')")
    private AttendanceSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
