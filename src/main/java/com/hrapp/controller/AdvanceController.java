package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ActionAdvanceRequest;
import com.hrapp.dto.request.AdvanceRequest;
import com.hrapp.dto.response.AdvanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.enums.AdvanceStatus;
import com.hrapp.service.AdvanceService;
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
@RequestMapping("/advances")
@RequiredArgsConstructor
@Tag(name = "12. Advances")
public class AdvanceController {

    private final AdvanceService advanceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> requestAdvance(
            @Valid @RequestBody AdvanceRequest request) {
        AdvanceResponse data = advanceService.requestAdvance(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Advance requested successfully"));
    }

    /** Personal advances newest-first — ADMIN/HR/EMPLOYEE (web "my advances"). */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AdvanceResponse>>> getMyAdvances() {
        List<AdvanceResponse> data = advanceService.getMyAdvances();
        return ResponseEntity.ok(ApiResponse.success(data, "Advances fetched successfully"));
    }

    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> cancelAdvance(@PathVariable("id") Long id) {
        AdvanceResponse data = advanceService.cancelAdvance(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Advance cancelled successfully"));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<AdvanceResponse>>> getPendingAdvances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AdvanceResponse> data = advanceService.getPendingAdvances(page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Pending advances fetched successfully"));
    }

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<AdvanceResponse>>> getCompanyAdvances(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AdvanceResponse> data =
                advanceService.getCompanyAdvances(status, employeeId, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Advances fetched successfully"));
    }

    @PutMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> actionAdvance(
            @PathVariable("id") Long id,
            @Valid @RequestBody ActionAdvanceRequest request) {
        AdvanceResponse data = advanceService.actionAdvance(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Advance action recorded successfully"));
    }
}
