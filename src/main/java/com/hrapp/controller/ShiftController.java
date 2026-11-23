package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.AssignShiftRequest;
import com.hrapp.dto.request.ShiftRequest;
import com.hrapp.dto.response.ResolvedShiftResponse;
import com.hrapp.dto.response.ShiftAssignmentResponse;
import com.hrapp.dto.response.ShiftResponse;
import com.hrapp.service.ShiftAssignmentService;
import com.hrapp.service.ShiftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/shifts")
@RequiredArgsConstructor
@Tag(name = "16. Shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftAssignmentService shiftAssignmentService;

    // ── Shift master (Phase 1) ─────────────────────────────────

    @PostMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<ShiftResponse>> createShift(
            @PathVariable Long companyId,
            @Valid @RequestBody ShiftRequest request) {
        ShiftResponse response = shiftService.createShift(companyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Shift created successfully"));
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> listShifts(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<ShiftResponse> shifts = shiftService.listShifts(companyId, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(shifts, "Shifts retrieved successfully"));
    }

    // Literal paths before /{id} so "assignments" / "resolve" are not captured as ids.

    @PostMapping("/company/{companyId}/assignments")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> assignShift(
            @PathVariable Long companyId,
            @Valid @RequestBody AssignShiftRequest request) {
        ShiftAssignmentResponse response = shiftAssignmentService.assignShift(companyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Shift assigned successfully"));
    }

    @GetMapping("/company/{companyId}/assignments/employee/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ShiftAssignmentResponse>>> assignmentHistory(
            @PathVariable Long companyId,
            @PathVariable Long userId) {
        List<ShiftAssignmentResponse> data =
                shiftAssignmentService.getAssignmentHistory(companyId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Shift assignment history retrieved"));
    }

    @GetMapping("/company/{companyId}/resolve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ResolvedShiftResponse>> resolveShift(
            @PathVariable Long companyId,
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ResolvedShiftResponse data = shiftAssignmentService.resolveForUser(companyId, userId, date);
        return ResponseEntity.ok(ApiResponse.success(data, "Shift resolved successfully"));
    }

    @GetMapping("/my/resolve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ResolvedShiftResponse>> resolveMyShift(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ResolvedShiftResponse data = shiftAssignmentService.resolveMine(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Shift resolved successfully"));
    }

    @GetMapping("/company/{companyId}/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShiftResponse>> getShift(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        ShiftResponse response = shiftService.getShift(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift retrieved successfully"));
    }

    @PutMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<ShiftResponse>> updateShift(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody ShiftRequest request) {
        ShiftResponse response = shiftService.updateShift(companyId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift updated successfully"));
    }

    @DeleteMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<ShiftResponse>> deactivateShift(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        ShiftResponse response = shiftService.deactivateShift(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift deactivated successfully"));
    }

    @PostMapping("/company/{companyId}/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<ShiftResponse>> reactivateShift(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        ShiftResponse response = shiftService.reactivateShift(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift reactivated successfully"));
    }
}
