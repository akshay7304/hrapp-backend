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
 * Holiday CRUD. Writes are restricted to SUPERADMIN at the controller layer,
 * so the {@code companyId} arg here is always trusted on writes. Reads accept
 * any authenticated user and silently scope to the caller's own company when
 * the caller is not SUPERADMIN — preventing cross-tenant data leaks.
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
        if (holidayRepository.existsByCompanyIdAndHolidayDate(companyId, request.getHolidayDate())) {
            throw new ConflictException("A holiday already exists on this date");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Holiday holiday = Holiday.builder()
                .company(company)
                .name(request.getName())
                .holidayDate(request.getHolidayDate())
                .build();
        holiday = holidayRepository.save(holiday);

        log.info("Created holiday id={} date={} in company={}", holiday.getId(), holiday.getHolidayDate(), companyId);
        return toResponse(holiday);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> getHolidays(Long companyId) {
        Long effectiveCompanyId = resolveReadCompanyId(companyId);
        return holidayRepository.findByCompanyIdOrderByHolidayDateAsc(effectiveCompanyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HolidayResponse updateHoliday(Long companyId, Long id, HolidayRequest request) {
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        if (!holiday.getHolidayDate().equals(request.getHolidayDate())
                && holidayRepository.existsByCompanyIdAndHolidayDate(companyId, request.getHolidayDate())) {
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
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        holidayRepository.delete(holiday);
        log.info("Deleted holiday id={}", id);
    }

    /**
     * SUPERADMIN can read any company's holidays; everyone else is locked to
     * their own company regardless of what they pass in the path.
     */
    private Long resolveReadCompanyId(Long pathCompanyId) {
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
