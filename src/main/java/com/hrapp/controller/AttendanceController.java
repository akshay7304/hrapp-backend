package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ManualAttendanceRequest;
import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.service.AttendanceService;
import com.hrapp.security.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Tag(name = "09. Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * FE GUIDANCE — Multiple punch support:
     * - Call check-in when employee starts or resumes work
     * - Call check-out when employee stops work (any time while checked in)
     * - After checkout, employee can check in again the same day
     * - Use isCheckedIn for the home-screen button:
     *   isCheckedIn = true → show "Check Out"
     *   isCheckedIn = false → show "Check In"
     * - Show punches list so employee can see all sessions
     * - workedHours = sum of all IN→OUT pairs (gaps excluded)
     * - Do NOT show attendance.status on home screen (reports only)
     */
    @PostMapping("/check-in")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn() {
        AttendanceResponse response = attendanceService.checkIn();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Checked in successfully"));
    }

    /**
     * FE GUIDANCE:
     * Employee may check out at any time while isCheckedIn = true.
     * Multiple check-in/check-out cycles per day are allowed.
     */
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
    public ResponseEntity<ApiResponse<PageResponse<AttendanceResponse>>> getMyAttendanceHistory(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AttendanceResponse> history =
                attendanceService.getMyAttendanceHistory(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Attendance history retrieved successfully"));
    }

    @GetMapping("/company/today")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<AttendanceResponse>>> getCompanyAttendanceToday(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AttendanceResponse> attendance = attendanceService.getCompanyAttendanceToday(pageable);
        return ResponseEntity.ok(ApiResponse.success(attendance, "Company attendance retrieved successfully"));
    }

    @GetMapping("/employee/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<AttendanceResponse>>> getEmployeeAttendanceHistory(
            @PathVariable("id") Long employeeId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AttendanceResponse> history =
                attendanceService.getEmployeeAttendanceHistory(employeeId, month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Employee attendance history retrieved successfully"));
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markManualAttendance(
            @Valid @RequestBody ManualAttendanceRequest request) {
        AttendanceResponse response = attendanceService.markManualAttendance(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Manual attendance recorded successfully"));
    }

    @PostMapping("/auto-checkout")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<String>> triggerAutoCheckout(
            @RequestParam(required = false) Long companyId) {
        Long effectiveCompanyId = companyId != null ? companyId : SecurityUtil.getCurrentUserCompanyId();
        if (effectiveCompanyId == null) {
            throw new BadRequestException("companyId is required when caller is not bound to a company");
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId != null && !callerCompanyId.equals(effectiveCompanyId)) {
            throw new UnauthorizedException("Cannot run auto checkout for another company");
        }
        int count = attendanceService.autoCheckoutMissed(effectiveCompanyId);
        return ResponseEntity.ok(ApiResponse.success(
                null,
                "Auto checkout processed " + count + " record(s) for company " + effectiveCompanyId));
    }
}
