package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed company payload — same shape as {@link CompanyResponse} plus the
 * onboarding bundle (departments / designations / leave types / holidays /
 * admin contact). Uses composition rather than Java inheritance so Lombok
 * {@code @Builder} stays simple and the JSON layout is predictable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDetailResponse {

    private Long id;
    private String name;
    private String address;
    private String logoUrl;
    private Boolean isActive;
    private Integer totalEmployees;
    private LocalDateTime createdAt;

    private List<DepartmentResponse> departments;
    private List<DesignationResponse> designations;
    private List<LeaveTypeResponse> leaveTypes;
    private List<HolidayResponse> holidays;

    private String adminName;
    private String adminMobile;
}
