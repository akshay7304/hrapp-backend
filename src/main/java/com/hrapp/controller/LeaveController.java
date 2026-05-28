package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ActionLeaveRequest;
import com.hrapp.dto.request.ApplyLeaveRequest;
import com.hrapp.dto.response.LeaveBalanceResponse;
import com.hrapp.dto.response.LeaveRequestResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.service.LeaveService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
@Tag(name = "10. Leave Management")
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping("/balances/my")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getMyLeaveBalances(
            @RequestParam Integer year) {
        List<LeaveBalanceResponse> data = leaveService.getMyLeaveBalances(year);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave balances fetched successfully"));
    }

    @GetMapping("/balances/employee/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getEmployeeLeaveBalances(
            @PathVariable("id") Long id,
            @RequestParam Integer year) {
        List<LeaveBalanceResponse> data = leaveService.getEmployeeLeaveBalances(id, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave balances fetched successfully"));
    }

    /**
     * Retro-init leave balances for an existing employee — meant for users that
     * were created before automatic initialization on hire was in place. Safe to
     * call repeatedly: the service skips leave types that already have a balance
     * row for the current year.
     */
    @PostMapping("/initialize/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> initializeLeaveBalances(
            @PathVariable("userId") Long userId) {
        List<LeaveBalanceResponse> data = leaveService.initializeLeaveBalancesManually(userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave balances initialized successfully"));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> applyLeave(
            @Valid @RequestBody ApplyLeaveRequest request) {
        LeaveRequestResponse data = leaveService.applyLeave(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Leave applied successfully"));
    }

    @PutMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> actionLeave(
            @PathVariable("id") Long id,
            @Valid @RequestBody ActionLeaveRequest request) {
        LeaveRequestResponse data = leaveService.actionLeave(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave action recorded successfully"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getMyLeaveRequests(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<LeaveRequestResponse> data = leaveService.getMyLeaveRequests(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave requests fetched successfully"));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getPendingLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<LeaveRequestResponse> data = leaveService.getPendingLeaveRequests(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Pending leave requests fetched successfully"));
    }

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getCompanyLeaveRequests(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<LeaveRequestResponse> data = leaveService.getCompanyLeaveRequests(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave requests fetched successfully"));
    }

    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancelLeave(@PathVariable("id") Long id) {
        LeaveRequestResponse data = leaveService.cancelLeave(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave cancelled successfully"));
    }

    @DeleteMapping("/{id}/admin-cancel")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> adminCancelLeave(@PathVariable("id") Long id) {
        LeaveRequestResponse data = leaveService.adminCancelLeave(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Leave cancelled successfully"));
    }
}
