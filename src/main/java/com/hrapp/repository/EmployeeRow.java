package com.hrapp.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One employee directory row: user columns plus department, designation,
 * status, and role names aggregated in the same query.
 */
public record EmployeeRow(
        Long id,
        String empCode,
        String fullName,
        String mobile,
        String email,
        String salaryType,
        LocalDate joiningDate,
        Long departmentId,
        String departmentName,
        Long designationId,
        String designationName,
        Long statusId,
        String statusName,
        Long companyId,
        String profilePicUrl,
        String thumbDeviceId,
        LocalDateTime createdAt,
        String roleNames
) {
}
