package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.EsslLogRequest;
import com.hrapp.dto.request.GenericLogRequest;
import com.hrapp.dto.request.ZktecoLogRequest;
import com.hrapp.dto.response.ThumbLogResponse;
import com.hrapp.dto.response.ThumbProcessResult;
import com.hrapp.exception.BadRequestException;
import com.hrapp.security.SecurityUtil;
import com.hrapp.service.ThumbScannerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/thumb")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "14. Thumb Scanner")
public class ThumbScannerController {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final ThumbScannerService thumbScannerService;

    // ============================================================
    //  Device push endpoints — unauthenticated; permitted in SecurityConfig
    //  and protected only by the per-company deviceSecret shared with HR
    // ============================================================

    @PostMapping("/zkteco")
    public ResponseEntity<ApiResponse<ThumbLogResponse>> processZktecoLog(
            @Valid @RequestBody ZktecoLogRequest request) {
        ThumbLogResponse data = thumbScannerService.processZktecoLog(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Punch recorded"));
    }

    @PostMapping("/essl")
    public ResponseEntity<ApiResponse<ThumbLogResponse>> processEsslLog(
            @Valid @RequestBody EsslLogRequest request) {
        ThumbLogResponse data = thumbScannerService.processEsslLog(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Punch recorded"));
    }

    @PostMapping("/generic")
    public ResponseEntity<ApiResponse<ThumbLogResponse>> processGenericLog(
            @Valid @RequestBody GenericLogRequest request) {
        ThumbLogResponse data = thumbScannerService.processGenericLog(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Punch recorded"));
    }

    // ============================================================
    //  Protected admin endpoints
    // ============================================================

    /**
     * Manually triggers the thumb-log → attendance compactor for the given
     * company. SUPERADMIN may pass any {@code companyId}; ADMIN is locked to
     * their own company regardless of what they send.
     */
    @PostMapping("/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ThumbProcessResult>> processThumbLogs(
            @RequestParam(required = false) Long companyId) {
        Long effectiveCompanyId = resolveCompanyIdForProcessing(companyId);
        ThumbProcessResult result = thumbScannerService.processThumbLogs(effectiveCompanyId);
        return ResponseEntity.ok(
                ApiResponse.success(result, "Thumb logs processed"));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<ThumbLogResponse>>> getPendingLogs(
            @RequestParam(required = false) Long companyId) {
        List<ThumbLogResponse> data = thumbScannerService.getPendingLogs(companyId);
        return ResponseEntity.ok(
                ApiResponse.success(data, "Pending thumb logs fetched"));
    }

    /**
     * SUPERADMIN may target any tenant via {@code companyId}; everyone else
     * is pinned to their own company even if they pass a different value.
     * Mirrors the convention used in {@code CompanySettingsService}.
     */
    private Long resolveCompanyIdForProcessing(Long requested) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN) && requested != null) {
            return requested;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId == null) {
            log.warn("Thumb /process called by a caller with no companyId");
            throw new BadRequestException("Caller is not bound to a company");
        }
        return callerCompanyId;
    }
}
