package com.hrapp.dto.request;

import com.hrapp.enums.SalaryType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeRequest {

    @NotNull(message = "Department is required")
    private Long departmentId;

    @NotNull(message = "Designation is required")
    private Long designationId;

    @NotBlank(message = "Employee code is required")
    private String empCode;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Mobile is required")
    @Pattern(regexp = "[0-9]{10}", message = "Mobile must be exactly 10 digits")
    private String mobile;

    @Email(message = "Invalid email format")
    private String email;

    @NotNull(message = "Salary type is required")
    private SalaryType salaryType;

    @NotNull(message = "Joining date is required")
    private LocalDate joiningDate;

    @NotNull(message = "Role is required")
    private Long roleId;
}
