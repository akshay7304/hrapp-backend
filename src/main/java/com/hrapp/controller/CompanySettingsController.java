package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.CompanySettingsRequest;
import com.hrapp.dto.response.CompanySettingsResponse;
import com.hrapp.service.CompanySettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/company-settings")
@RequiredArgsConstructor
@Tag(name = "06. Company Settings")
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<CompanySettingsResponse>> getSettings(
            @PathVariable Long companyId) {
        CompanySettingsResponse response = companySettingsService.getSettings(companyId);
        return ResponseEntity.ok(ApiResponse.success(response, "Company settings retrieved successfully"));
    }

    @PutMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<CompanySettingsResponse>> updateSettings(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanySettingsRequest request) {
        CompanySettingsResponse response = companySettingsService.updateSettings(companyId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Company settings updated successfully"));
    }
}
