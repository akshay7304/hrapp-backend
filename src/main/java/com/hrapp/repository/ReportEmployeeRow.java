package com.hrapp.repository;

/** Company employee identity used by paginated report list queries. */
public record ReportEmployeeRow(
        Long userId,
        String fullName,
        String empCode,
        String departmentName,
        String designationName
) {
}
