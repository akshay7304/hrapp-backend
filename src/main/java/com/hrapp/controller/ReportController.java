package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.response.AdvanceReportResponse;
import com.hrapp.dto.response.AttendanceReportResponse;
import com.hrapp.dto.response.LeaveReportResponse;
import com.hrapp.dto.response.SalaryReportResponse;
import com.hrapp.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@Tag(name = "13. Reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceReportResponse>>> getMonthlyAttendanceReport(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        List<AttendanceReportResponse> data = reportService.getMonthlyAttendanceReport(month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Attendance report generated successfully"));
    }

    @GetMapping("/salary")
    public ResponseEntity<ApiResponse<List<SalaryReportResponse>>> getMonthlySalaryReport(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        List<SalaryReportResponse> data = reportService.getMonthlySalaryReport(month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Salary report generated successfully"));
    }

    @GetMapping("/leave")
    public ResponseEntity<ApiResponse<List<LeaveReportResponse>>> getLeaveReport(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        List<LeaveReportResponse> data = reportService.getLeaveReport(month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave report generated successfully"));
    }

    @GetMapping("/advances")
    public ResponseEntity<ApiResponse<List<AdvanceReportResponse>>> getAdvanceReport() {
        List<AdvanceReportResponse> data = reportService.getAdvanceReport();
        return ResponseEntity.ok(ApiResponse.success(data, "Advance report generated successfully"));
    }
}
