package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.CreateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyStatusRequest;
import com.hrapp.dto.response.CompanyDetailResponse;
import com.hrapp.dto.response.CompanyResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.service.CompanyService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "02. Company Management")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyDetailResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyDetailResponse data = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Company created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CompanyResponse>>> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<CompanyResponse> data = companyService.getAllCompanies(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Companies fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyDetailResponse>> getCompanyById(@PathVariable("id") Long id) {
        CompanyDetailResponse data = companyService.getCompanyById(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Company fetched successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        CompanyResponse data = companyService.updateCompany(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Company updated successfully"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompanyStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateCompanyStatusRequest request) {
        CompanyResponse data = companyService.updateCompanyStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Company status updated successfully"));
    }
}
