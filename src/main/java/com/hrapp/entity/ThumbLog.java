package com.hrapp.entity;

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
@Table(name = "thumb_logs", indexes = {
        @Index(name = "idx_thumb_company_proc", columnList = "company_id, is_processed, punch_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "device_emp_id", nullable = false, length = 50)
    private String deviceEmpId;

    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false,
            columnDefinition = "ENUM('IN','OUT')")
    private PunchType punchType;

    @Column(name = "is_processed", nullable = false)
    @Builder.Default
    private Boolean isProcessed = false;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private LocalDateTime syncedAt;
}
