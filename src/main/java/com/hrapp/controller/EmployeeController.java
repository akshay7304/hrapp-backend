package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.CreateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeStatusRequest;
import com.hrapp.dto.response.EmployeeResponse;
import com.hrapp.service.EmployeeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@Tag(name = "03. Employee Management")
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Employee created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<EmployeeResponse>>> getAllEmployees() {
        List<EmployeeResponse> employees = employeeService.getAllEmployees();
        return ResponseEntity.ok(ApiResponse.success(employees, "Employees retrieved successfully"));
    }

    /**
     * Declared before {@code /{id}} so {@code GET /employees/profile} routes
     * to this method even though the literal/variable mapping order doesn't
     * strictly matter in Spring MVC — clearer for readers.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getMyProfile() {
        EmployeeResponse response = employeeService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success(response, "Profile retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployeeById(@PathVariable Long id) {
        EmployeeResponse response = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        EmployeeResponse response = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee updated successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployeeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeStatusRequest request) {
        EmployeeResponse response = employeeService.updateEmployeeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee status updated successfully"));
    }
}
