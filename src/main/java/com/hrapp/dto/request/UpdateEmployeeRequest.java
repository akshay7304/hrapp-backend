package com.hrapp.dto.request;

import com.hrapp.enums.SalaryType;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Partial-update payload — every field is optional. Only the fields the
 * caller sets (non-null) are applied to the employee record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmployeeRequest {

    private Long departmentId;

    private Long designationId;

    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    private SalaryType salaryType;

    private LocalDate joiningDate;

    /** Optional — EMPLOYEE or HR only (same rules as create). ADMIN via company admin APIs. */
    private Long roleId;

    /** Biometric enrol id on the device (maps punches → this user). */
    private String thumbDeviceId;

    /** Profile photo URL (FE/CDN). Blank clears. */
    private String profilePicUrl;
}
