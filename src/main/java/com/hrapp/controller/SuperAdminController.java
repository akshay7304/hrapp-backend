package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.CreateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyStatusRequest;
import com.hrapp.dto.response.CompanyDetailResponse;
import com.hrapp.dto.response.CompanyResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.service.SuperAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPERADMIN company-management facade. Dashboard lives on {@code GET /home}
 * ({@code platform} block). Canonical CRUD also remains on {@code /companies}.
 */
@RestController
@RequestMapping("/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "01. SuperAdmin")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<CompanyDetailResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyDetailResponse data = superAdminService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Company created successfully"));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<PageResponse<CompanyResponse>>> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<CompanyResponse> data = superAdminService.getAllCompanies(page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Companies fetched successfully"));
    }

    @GetMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyDetailResponse>> getCompanyById(
            @PathVariable("id") Long id) {
        CompanyDetailResponse data = superAdminService.getCompanyById(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Company fetched successfully"));
    }

    @PutMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        CompanyResponse data = superAdminService.updateCompany(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Company updated successfully"));
    }

    @PutMapping("/companies/{id}/status")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompanyStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateCompanyStatusRequest request) {
        CompanyResponse data = superAdminService.updateCompanyStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Company status updated successfully"));
    }
}
