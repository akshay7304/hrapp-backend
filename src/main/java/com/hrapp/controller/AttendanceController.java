package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ManualAttendanceRequest;
import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.service.AttendanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Tag(name = "09. Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn() {
        AttendanceResponse response = attendanceService.checkIn();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Checked in successfully"));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut() {
        AttendanceResponse response = attendanceService.checkOut();
        return ResponseEntity.ok(ApiResponse.success(response, "Checked out successfully"));
    }

    @GetMapping("/today")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getTodayAttendance() {
        AttendanceResponse response = attendanceService.getTodayAttendance();
        return ResponseEntity.ok(ApiResponse.success(response, "Today's attendance retrieved successfully"));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMyAttendanceHistory(
            @RequestParam int month,
            @RequestParam int year) {
        List<AttendanceResponse> history = attendanceService.getMyAttendanceHistory(month, year);
        return ResponseEntity.ok(ApiResponse.success(history, "Attendance history retrieved successfully"));
    }

    @GetMapping("/company/today")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getCompanyAttendanceToday() {
        List<AttendanceResponse> attendance = attendanceService.getCompanyAttendanceToday();
        return ResponseEntity.ok(ApiResponse.success(attendance, "Company attendance retrieved successfully"));
    }

    @GetMapping("/employee/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getEmployeeAttendanceHistory(
            @PathVariable("id") Long employeeId,
            @RequestParam int month,
            @RequestParam int year) {
        List<AttendanceResponse> history =
                attendanceService.getEmployeeAttendanceHistory(employeeId, month, year);
        return ResponseEntity.ok(ApiResponse.success(history, "Employee attendance history retrieved successfully"));
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markManualAttendance(
            @Valid @RequestBody ManualAttendanceRequest request) {
        AttendanceResponse response = attendanceService.markManualAttendance(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Manual attendance recorded successfully"));
    }
}
