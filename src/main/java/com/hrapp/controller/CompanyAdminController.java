package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.AssignAdminRequest;
import com.hrapp.dto.response.EmployeeResponse;
import com.hrapp.service.CompanyAdminService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company multi-admin management. Separate from {@link CompanyController}
 * (SUPERADMIN-only) so company ADMINS can list/assign/revoke peers.
 * <p>
 * Multiple admins per company are allowed; the last admin cannot be revoked.
 */
@RestController
@RequestMapping("/companies/{companyId}/admins")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
@Tag(name = "02b. Company Admins")
public class CompanyAdminController {

    private final CompanyAdminService companyAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeResponse>>> listAdmins(
            @PathVariable("companyId") Long companyId) {
        List<EmployeeResponse> data = companyAdminService.listAdmins(companyId);
        return ResponseEntity.ok(ApiResponse.success(data, "Company admins fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponse>> assignAdmin(
            @PathVariable("companyId") Long companyId,
            @Valid @RequestBody AssignAdminRequest request) {
        EmployeeResponse data = companyAdminService.assignAdmin(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Admin assigned successfully"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> revokeAdmin(
            @PathVariable("companyId") Long companyId,
            @PathVariable("userId") Long userId) {
        EmployeeResponse data = companyAdminService.revokeAdmin(companyId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Admin revoked successfully"));
    }
}
