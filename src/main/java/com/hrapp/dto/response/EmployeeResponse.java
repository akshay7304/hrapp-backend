package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {

    private Long id;
    private String empCode;
    private String fullName;
    private String mobile;
    private String email;
    private String salaryType;
    private LocalDate joiningDate;

    private Long departmentId;
    private String departmentName;

    private Long designationId;
    private String designationName;

    private Long statusId;
    private String statusName;

    private Long companyId;

    private List<String> roles;

    /** Profile photo URL when set; {@code null} if the employee has no photo. */
    private String avatar;

    /** Biometric enrol id when set. */
    private String thumbDeviceId;

    /**
     * One-time temporary password returned only on create.
     * FE must show it once; never returned on subsequent GETs.
     */
    private String temporaryPassword;

    private LocalDateTime createdAt;
}
