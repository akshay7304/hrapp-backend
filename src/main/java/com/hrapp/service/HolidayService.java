package com.hrapp.service;

import com.hrapp.dto.request.HolidayRequest;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Holiday;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Holiday CRUD. SUPERADMIN can manage any company; ADMIN is locked to their
 * own company regardless of the path {@code companyId}. Reads are open to any
 * authenticated user but similarly scoped for non-SUPERADMIN callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HolidayService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final HolidayRepository holidayRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public HolidayResponse createHoliday(Long companyId, HolidayRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        if (holidayRepository.existsByCompanyIdAndHolidayDate(effectiveCompanyId, request.getHolidayDate())) {
            throw new ConflictException("A holiday already exists on this date");
        }

        Company company = companyRepository.findById(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Holiday holiday = Holiday.builder()
                .company(company)
                .name(request.getName())
                .holidayDate(request.getHolidayDate())
                .build();
        holiday = holidayRepository.save(holiday);

        log.info("Created holiday id={} date={} in company={}",
                holiday.getId(), holiday.getHolidayDate(), effectiveCompanyId);
        return toResponse(holiday);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> getHolidays(Long companyId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        return holidayRepository.findByCompanyIdOrderByHolidayDateAsc(effectiveCompanyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HolidayResponse updateHoliday(Long companyId, Long id, HolidayRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        if (!holiday.getHolidayDate().equals(request.getHolidayDate())
                && holidayRepository.existsByCompanyIdAndHolidayDate(effectiveCompanyId, request.getHolidayDate())) {
            throw new ConflictException("A holiday already exists on this date");
        }

        holiday.setName(request.getName());
        holiday.setHolidayDate(request.getHolidayDate());
        holiday = holidayRepository.save(holiday);

        log.info("Updated holiday id={} date={}", holiday.getId(), holiday.getHolidayDate());
        return toResponse(holiday);
    }

    @Transactional
    public void deleteHoliday(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        holidayRepository.delete(holiday);
        log.info("Deleted holiday id={}", id);
    }

    /**
     * SUPERADMIN can target any company by id; everyone else is locked to
     * their own company regardless of what they pass in the path.
     */
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

    private HolidayResponse toResponse(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .companyId(holiday.getCompany() != null ? holiday.getCompany().getId() : null)
                .createdAt(holiday.getCreatedAt())
                .build();
    }
}
