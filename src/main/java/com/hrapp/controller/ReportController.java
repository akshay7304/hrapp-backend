package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.response.AdvanceReportResponse;
import com.hrapp.dto.response.AttendanceReportResponse;
import com.hrapp.dto.response.LeaveReportResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryReportResponse;
import com.hrapp.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@Tag(name = "13. Reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<PageResponse<AttendanceReportResponse>>> getMonthlyAttendanceReport(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AttendanceReportResponse> data =
                reportService.getMonthlyAttendanceReport(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Attendance report generated successfully"));
    }

    @GetMapping("/salary")
    public ResponseEntity<ApiResponse<PageResponse<SalaryReportResponse>>> getMonthlySalaryReport(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<SalaryReportResponse> data =
                reportService.getMonthlySalaryReport(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Salary report generated successfully"));
    }

    @GetMapping("/leave")
    public ResponseEntity<ApiResponse<PageResponse<LeaveReportResponse>>> getLeaveReport(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<LeaveReportResponse> data =
                reportService.getLeaveReport(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave report generated successfully"));
    }

    @GetMapping("/advances")
    public ResponseEntity<ApiResponse<PageResponse<AdvanceReportResponse>>> getAdvanceReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AdvanceReportResponse> data = reportService.getAdvanceReport(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Advance report generated successfully"));
    }
}
