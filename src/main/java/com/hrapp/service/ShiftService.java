package com.hrapp.service;

import com.hrapp.dto.request.ShiftRequest;
import com.hrapp.dto.response.ShiftResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Shift;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.ShiftRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * Phase 1 shift master — company-defined shifts with optional single default.
 * No system-seeded shifts; ADMIN/HR create and maintain them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final ShiftRepository shiftRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public ShiftResponse createShift(Long companyId, ShiftRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        String name = normalizeName(request.getName());
        validateHours(request);
        boolean overnight = resolveOvernight(request);

        if (shiftRepository.existsByCompanyIdAndNameIgnoreCase(effectiveCompanyId, name)) {
            throw new ConflictException("Shift with this name already exists");
        }

        Company company = companyRepository.findById(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            shiftRepository.clearDefaultForCompany(effectiveCompanyId, null);
        }

        Shift shift = Shift.builder()
                .company(company)
                .name(name)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isOvernight(overnight)
                .graceLateMinutes(request.getGraceLateMinutes())
                .fullDayHours(request.getFullDayHours())
                .halfDayHours(request.getHalfDayHours())
                .breakMinutes(request.getBreakMinutes())
                .breakPaid(Boolean.TRUE.equals(request.getBreakPaid()))
                .isDefault(makeDefault)
                .isActive(true)
                .build();
        shift = shiftRepository.save(shift);

        log.info("Created shift id={} name='{}' company={}", shift.getId(), shift.getName(), effectiveCompanyId);
        return toResponse(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listShifts(Long companyId, boolean activeOnly) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        List<Shift> shifts = activeOnly
                ? shiftRepository.findByCompanyIdAndIsActiveTrueOrderByNameAsc(effectiveCompanyId)
                : shiftRepository.findByCompanyIdOrderByNameAsc(effectiveCompanyId);
        return shifts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse getShift(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Shift shift = shiftRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse updateShift(Long companyId, Long id, ShiftRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Shift shift = shiftRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (!Boolean.TRUE.equals(shift.getIsActive())) {
            throw new BadRequestException("Cannot update an inactive shift — reactivate it first");
        }

        String name = normalizeName(request.getName());
        validateHours(request);
        boolean overnight = resolveOvernight(request);

        if (shiftRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(effectiveCompanyId, name, id)) {
            throw new ConflictException("Shift with this name already exists");
        }

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            shiftRepository.clearDefaultForCompany(effectiveCompanyId, id);
        }

        shift.setName(name);
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setIsOvernight(overnight);
        shift.setGraceLateMinutes(request.getGraceLateMinutes());
        shift.setFullDayHours(request.getFullDayHours());
        shift.setHalfDayHours(request.getHalfDayHours());
        shift.setBreakMinutes(request.getBreakMinutes());
        shift.setBreakPaid(Boolean.TRUE.equals(request.getBreakPaid()));
        shift.setIsDefault(makeDefault);
        shift = shiftRepository.save(shift);

        log.info("Updated shift id={} name='{}'", shift.getId(), shift.getName());
        return toResponse(shift);
    }

    /**
     * Soft-deactivate. Clears default flag so another shift can become default.
     */
    @Transactional
    public ShiftResponse deactivateShift(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Shift shift = shiftRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (!Boolean.TRUE.equals(shift.getIsActive())) {
            return toResponse(shift);
        }
        shift.setIsActive(false);
        shift.setIsDefault(false);
        shift = shiftRepository.save(shift);
        log.info("Deactivated shift id={} company={}", id, effectiveCompanyId);
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse reactivateShift(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Shift shift = shiftRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (Boolean.TRUE.equals(shift.getIsActive())) {
            return toResponse(shift);
        }
        if (shiftRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(
                effectiveCompanyId, shift.getName(), id)) {
            throw new ConflictException(
                    "Cannot reactivate: another shift already uses the name '" + shift.getName() + "'");
        }
        shift.setIsActive(true);
        shift = shiftRepository.save(shift);
        log.info("Reactivated shift id={} company={}", id, effectiveCompanyId);
        return toResponse(shift);
    }

    private void validateHours(ShiftRequest request) {
        if (request.getHalfDayHours().compareTo(request.getFullDayHours()) > 0) {
            throw new BadRequestException("Half day hours cannot exceed full day hours");
        }
    }

    /**
     * Overnight when explicitly flagged, or when end is not after start
     * (cross-midnight). Same start/end is rejected unless overnight (rare 24h).
     */
    private boolean resolveOvernight(ShiftRequest request) {
        LocalTime start = request.getStartTime();
        LocalTime end = request.getEndTime();
        boolean flagged = Boolean.TRUE.equals(request.getIsOvernight());
        if (!end.isAfter(start)) {
            if (!flagged && !end.equals(start)) {
                // end before start → must be overnight
                return true;
            }
            if (end.equals(start) && !flagged) {
                throw new BadRequestException(
                        "Start and end time are equal — set isOvernight=true for a 24-hour shift, "
                                + "or choose different times");
            }
            return true;
        }
        return flagged;
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private Long resolveCompanyId(Long pathCompanyId) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            if (pathCompanyId == null) {
                throw new BadRequestException("companyId is required");
            }
            companyRepository.findById(pathCompanyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
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

    private ShiftResponse toResponse(Shift shift) {
        return ShiftResponse.builder()
                .id(shift.getId())
                .companyId(shift.getCompany() != null ? shift.getCompany().getId() : null)
                .name(shift.getName())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .isOvernight(shift.getIsOvernight())
                .graceLateMinutes(shift.getGraceLateMinutes())
                .fullDayHours(shift.getFullDayHours())
                .halfDayHours(shift.getHalfDayHours())
                .breakMinutes(shift.getBreakMinutes())
                .breakPaid(shift.getBreakPaid())
                .isDefault(shift.getIsDefault())
                .isActive(shift.getIsActive())
                .createdAt(shift.getCreatedAt())
                .updatedAt(shift.getUpdatedAt())
                .build();
    }
}
