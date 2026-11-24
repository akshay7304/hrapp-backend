package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Dropdown payload for create / edit employee screens —
 * departments, designations, assignable roles, and salary types in one call.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSetupResponse {

    private List<DepartmentResponse> departments;
    private List<DesignationResponse> designations;
    private List<RoleResponse> roles;
    private List<String> salaryTypes;
}
