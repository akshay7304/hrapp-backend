package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.LeaveTypeRequest;
import com.hrapp.dto.response.LeaveTypeResponse;
import com.hrapp.service.LeaveTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/leave-types")
@RequiredArgsConstructor
@Tag(name = "08. Leave Types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    @PostMapping("/company/{companyId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> createLeaveType(
            @PathVariable Long companyId,
            @Valid @RequestBody LeaveTypeRequest request) {
        LeaveTypeResponse response = leaveTypeService.createLeaveType(companyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Leave type created successfully"));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getLeaveTypes(
            @PathVariable Long companyId) {
        List<LeaveTypeResponse> leaveTypes = leaveTypeService.getLeaveTypes(companyId);
        return ResponseEntity.ok(ApiResponse.success(leaveTypes, "Leave types retrieved successfully"));
    }

    @PutMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> updateLeaveType(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody LeaveTypeRequest request) {
        LeaveTypeResponse response = leaveTypeService.updateLeaveType(companyId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave type updated successfully"));
    }

    @DeleteMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLeaveType(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        leaveTypeService.deleteLeaveType(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Leave type deleted successfully"));
    }
}
