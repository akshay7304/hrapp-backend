package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.ActionAdvanceRequest;
import com.hrapp.dto.request.AdvanceRequest;
import com.hrapp.dto.response.AdvanceResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/advances")
@RequiredArgsConstructor
@Tag(name = "12. Advances")
public class AdvanceController {

    private final AdvanceService advanceService;

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> requestAdvance(
            @Valid @RequestBody AdvanceRequest request) {
        AdvanceResponse data = advanceService.requestAdvance(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Advance requested successfully"));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AdvanceResponse>>> getMyAdvances() {
        List<AdvanceResponse> data = advanceService.getMyAdvances();
        return ResponseEntity.ok(ApiResponse.success(data, "Advances fetched successfully"));
    }

    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> cancelAdvance(@PathVariable("id") Long id) {
        AdvanceResponse data = advanceService.cancelAdvance(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Advance cancelled successfully"));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<AdvanceResponse>>> getPendingAdvances() {
        List<AdvanceResponse> data = advanceService.getPendingAdvances();
        return ResponseEntity.ok(ApiResponse.success(data, "Pending advances fetched successfully"));
    }

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<AdvanceResponse>>> getCompanyAdvances() {
        List<AdvanceResponse> data = advanceService.getCompanyAdvances();
        return ResponseEntity.ok(ApiResponse.success(data, "Advances fetched successfully"));
    }

    @PutMapping("/{id}/action")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdvanceResponse>> actionAdvance(
            @PathVariable("id") Long id,
            @Valid @RequestBody ActionAdvanceRequest request) {
        AdvanceResponse data = advanceService.actionAdvance(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Advance action recorded successfully"));
    }
}
