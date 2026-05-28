package com.hrapp.service;

import com.hrapp.dto.request.CompanySettingsRequest;
import com.hrapp.dto.response.CompanySettingsResponse;
import com.hrapp.entity.CompanySettings;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads/updates per-company attendance configuration. SUPERADMIN can target
 * any company by id; everyone else can only act on their own company —
 * regardless of what they put in the path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanySettingsService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final CompanySettingsRepository companySettingsRepository;
    @SuppressWarnings("unused")
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanySettingsResponse getSettings(Long companyId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        CompanySettings settings = companySettingsRepository.findByCompanyId(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company settings not configured"));
        return toResponse(settings);
    }

    @Transactional
    public CompanySettingsResponse updateSettings(Long companyId, CompanySettingsRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        CompanySettings settings = companySettingsRepository.findByCompanyId(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company settings not configured"));

        settings.setShiftStartTime(request.getShiftStartTime());
        settings.setShiftEndTime(request.getShiftEndTime());
        settings.setShiftHours(request.getShiftHours());
        settings.setHalfDayHours(request.getHalfDayHours());
        settings.setOvertimeAfterHours(request.getOvertimeAfterHours());
        settings.setLateMarkAfterMinutes(request.getLateMarkAfterMinutes());
        settings.setWeekOffDay(request.getWeekOffDay());
        settings.setDeviceBrand(request.getDeviceBrand());
        settings.setDeviceSecret(request.getDeviceSecret());

        settings = companySettingsRepository.save(settings);
        log.info("Updated company settings for company={}", effectiveCompanyId);
        return toResponse(settings);
    }

    private Long resolveCompanyId(Long pathCompanyId) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            if (pathCompanyId == null) {
                throw new ResourceNotFoundException("Company not found");
            }
            return pathCompanyId;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId == null) {
            throw new UnauthorizedException("Caller is not bound to a company");
        }
        return callerCompanyId;
    }

    private CompanySettingsResponse toResponse(CompanySettings settings) {
        return CompanySettingsResponse.builder()
                .id(settings.getId())
                .companyId(settings.getCompany() != null ? settings.getCompany().getId() : null)
                .shiftStartTime(settings.getShiftStartTime())
                .shiftEndTime(settings.getShiftEndTime())
                .shiftHours(settings.getShiftHours())
                .halfDayHours(settings.getHalfDayHours())
                .overtimeAfterHours(settings.getOvertimeAfterHours())
                .lateMarkAfterMinutes(settings.getLateMarkAfterMinutes())
                .weekOffDay(settings.getWeekOffDay())
                .deviceBrand(settings.getDeviceBrand())
                .deviceSecret(settings.getDeviceSecret())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
