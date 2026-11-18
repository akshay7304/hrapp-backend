package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.response.EmployeeDashboardResponse;
import com.hrapp.dto.response.OpsDashboardResponse;
import com.hrapp.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "15. Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Employee home-screen aggregate.
     * FE: use todayAttendance.isCheckedIn for Check In / Check Out button;
     * monthSummary for quick month stats; pending*Count for badges.
     */
    @GetMapping("/employee")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeDashboardResponse>> getEmployeeDashboard() {
        EmployeeDashboardResponse data = dashboardService.getEmployeeDashboard();
        return ResponseEntity.ok(ApiResponse.success(data, "Employee dashboard retrieved successfully"));
    }

    /**
     * HR/ADMIN company home-screen aggregate: pending leaves/advances/thumb logs,
     * today's attendance pulse, active headcount, and this-month payroll progress.
     */
    @GetMapping("/ops")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<OpsDashboardResponse>> getOpsDashboard() {
        OpsDashboardResponse data = dashboardService.getOpsDashboard();
        return ResponseEntity.ok(ApiResponse.success(data, "Ops dashboard retrieved successfully"));
    }
}
