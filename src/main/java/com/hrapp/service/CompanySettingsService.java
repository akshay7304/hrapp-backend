package com.hrapp.service;

import com.hrapp.dto.request.CompanySettingsRequest;
import com.hrapp.dto.response.CompanySettingsResponse;
import com.hrapp.entity.CompanySettings;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.security.SecurityUtil;
import com.hrapp.enums.LeaveCountType;
import com.hrapp.util.WeekOffSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        settings.setWeekOffDay(resolveWeekOffStorage(request));
        if (request.getLeaveCountType() != null && !request.getLeaveCountType().isBlank()) {
            settings.setLeaveCountType(resolveLeaveCountType(request.getLeaveCountType()));
        } else if (settings.getLeaveCountType() == null || settings.getLeaveCountType().isBlank()) {
            settings.setLeaveCountType(LeaveCountType.EXCLUDE_WEEK_OFF_AND_HOLIDAYS.name());
        }

        if (request.getDeviceBrand() != null && !request.getDeviceBrand().isBlank()) {
            settings.setDeviceBrand(request.getDeviceBrand().trim());
        } else if (settings.getDeviceBrand() == null || settings.getDeviceBrand().isBlank()) {
            settings.setDeviceBrand("ZKTECO");
        }

        if (request.getDeviceSerial() != null) {
            String serial = request.getDeviceSerial().isBlank() ? null : request.getDeviceSerial().trim();
            if (serial != null) {
                Long settingsId = settings.getId();
                companySettingsRepository.findByDeviceSerial(serial)
                        .filter(other -> !other.getId().equals(settingsId))
                        .ifPresent(other -> {
                            throw new ConflictException(
                                    "Device serial already registered to another company",
                                    Map.of("deviceSerial", "Already in use"));
                        });
            }
            settings.setDeviceSerial(serial);
        }

        // Write-only: only overwrite when the client sends a non-blank secret.
        if (request.getDeviceSecret() != null && !request.getDeviceSecret().isBlank()) {
            settings.setDeviceSecret(request.getDeviceSecret().trim());
        } else if (settings.getDeviceSecret() == null || settings.getDeviceSecret().isBlank()) {
            settings.setDeviceSecret(generateDeviceSecret());
        }

        settings = companySettingsRepository.save(settings);
        log.info("Updated company settings for company={}", effectiveCompanyId);
        return toResponse(settings);
    }

    /** Random secret for new companies / empty rows — never a shared default. */
    public static String generateDeviceSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveWeekOffStorage(CompanySettingsRequest request) {
        if (request.getWeekOffDays() != null && !request.getWeekOffDays().isEmpty()) {
            return WeekOffSupport.toStorage(request.getWeekOffDays());
        }
        if (request.getWeekOffDay() != null && !request.getWeekOffDay().isBlank()) {
            return WeekOffSupport.toStorage(List.of(request.getWeekOffDay().trim()));
        }
        throw new BadRequestException("weekOffDays (or legacy weekOffDay) is required");
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
        if (pathCompanyId != null && !pathCompanyId.equals(callerCompanyId)) {
            throw new UnauthorizedException("Cannot access another company's data");
        }
        return callerCompanyId;
    }

    private CompanySettingsResponse toResponse(CompanySettings settings) {
        String secret = settings.getDeviceSecret();
        return CompanySettingsResponse.builder()
                .id(settings.getId())
                .companyId(settings.getCompany() != null ? settings.getCompany().getId() : null)
                .shiftStartTime(settings.getShiftStartTime())
                .shiftEndTime(settings.getShiftEndTime())
                .shiftHours(settings.getShiftHours())
                .halfDayHours(settings.getHalfDayHours())
                .overtimeAfterHours(settings.getOvertimeAfterHours())
                .lateMarkAfterMinutes(settings.getLateMarkAfterMinutes())
                .weekOffDays(WeekOffSupport.toApiList(settings.getWeekOffDay()))
                .weekOffDay(firstWeekOff(settings.getWeekOffDay()))
                .leaveCountType(settings.getLeaveCountType())
                .deviceBrand(settings.getDeviceBrand())
                .deviceSerial(settings.getDeviceSerial())
                .deviceSecretConfigured(secret != null && !secret.isBlank())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    private static String resolveLeaveCountType(String raw) {
        try {
            return LeaveCountType.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Invalid leaveCountType. Use EXCLUDE_WEEK_OFF_AND_HOLIDAYS, EXCLUDE_WEEK_OFF, or CALENDAR_DAYS");
        }
    }

    private static String firstWeekOff(String stored) {
        List<String> days = WeekOffSupport.toApiList(stored);
        return days.isEmpty() ? null : days.get(0);
    }
}
