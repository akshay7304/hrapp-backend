package com.hrapp.entity;

import com.hrapp.enums.SalaryType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_mobile", columnNames = "mobile"),
                @UniqueConstraint(name = "uq_emp_code", columnNames = {"company_id", "emp_code"})
        },
        indexes = {
                @Index(name = "idx_users_company", columnList = "company_id, status_id"),
                @Index(name = "idx_users_thumb", columnList = "thumb_device_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(name = "emp_code", length = 20)
    private String empCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", columnDefinition = "ENUM('MONTHLY','DAILY','HOURLY') DEFAULT 'MONTHLY'")
    @Builder.Default
    private SalaryType salaryType = SalaryType.MONTHLY;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "thumb_device_id", length = 50)
    private String thumbDeviceId;

    @Column(name = "profile_pic_url", length = 255)
    private String profilePicUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
