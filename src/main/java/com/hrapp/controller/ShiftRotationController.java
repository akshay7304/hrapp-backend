package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ConfirmRotationDraftRequest;
import com.hrapp.dto.request.CreateRotationRequest;
import com.hrapp.dto.request.GenerateRotationDraftRequest;
import com.hrapp.dto.request.RotationMembersRequest;
import com.hrapp.dto.request.UpdateDraftRowRequest;
import com.hrapp.dto.request.UpdateRotationRequest;
import com.hrapp.dto.response.RotationDraftResponse;
import com.hrapp.dto.response.RotationMemberResponse;
import com.hrapp.dto.response.RotationResponse;
import com.hrapp.service.ShiftRotationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shifts/company/{companyId}/rotations")
@RequiredArgsConstructor
@Tag(name = "17. Shift Rotations")
public class ShiftRotationController {

    private final ShiftRotationService shiftRotationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationResponse>> create(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateRotationRequest request) {
        RotationResponse data = shiftRotationService.create(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Rotation created successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RotationResponse>>> list(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.list(companyId, activeOnly),
                "Rotations retrieved successfully"));
    }

    @GetMapping("/{rotationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RotationResponse>> get(
            @PathVariable Long companyId,
            @PathVariable Long rotationId) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.get(companyId, rotationId),
                "Rotation retrieved successfully"));
    }

    @PutMapping("/{rotationId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationResponse>> update(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @Valid @RequestBody UpdateRotationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.update(companyId, rotationId, request),
                "Rotation updated successfully"));
    }

    @DeleteMapping("/{rotationId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationResponse>> deactivate(
            @PathVariable Long companyId,
            @PathVariable Long rotationId) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.deactivate(companyId, rotationId),
                "Rotation deactivated successfully"));
    }

    @PostMapping("/{rotationId}/members")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RotationMemberResponse>>> addMembers(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @Valid @RequestBody RotationMembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.addMembers(companyId, rotationId, request),
                "Members added successfully"));
    }

    @GetMapping("/{rotationId}/members")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RotationMemberResponse>>> listMembers(
            @PathVariable Long companyId,
            @PathVariable Long rotationId) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.listMembers(companyId, rotationId),
                "Members retrieved successfully"));
    }

    @DeleteMapping("/{rotationId}/members/{userId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @PathVariable Long userId) {
        shiftRotationService.removeMember(companyId, rotationId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed successfully"));
    }

    @PostMapping("/{rotationId}/drafts/generate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationDraftResponse>> generateDraft(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @RequestBody(required = false) GenerateRotationDraftRequest request) {
        RotationDraftResponse data = shiftRotationService.generateDraft(
                companyId, rotationId, request != null ? request : new GenerateRotationDraftRequest());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Draft generated successfully"));
    }

    @GetMapping("/{rotationId}/drafts/current")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationDraftResponse>> getCurrentDraft(
            @PathVariable Long companyId,
            @PathVariable Long rotationId) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.getCurrentDraft(companyId, rotationId),
                "Draft retrieved successfully"));
    }

    @PutMapping("/{rotationId}/drafts/current/rows/{userId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationDraftResponse>> updateDraftRow(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateDraftRowRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.updateDraftRow(companyId, rotationId, userId, request),
                "Draft row updated successfully"));
    }

    @PostMapping("/{rotationId}/drafts/current/confirm")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RotationDraftResponse>> confirmDraft(
            @PathVariable Long companyId,
            @PathVariable Long rotationId,
            @Valid @RequestBody ConfirmRotationDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftRotationService.confirmDraft(companyId, rotationId, request),
                "Draft confirmed — assignments applied"));
    }
}
