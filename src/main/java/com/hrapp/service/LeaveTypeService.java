package com.hrapp.service;

import com.hrapp.dto.request.LeaveTypeRequest;
import com.hrapp.dto.response.LeaveTypeResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.LeaveType;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.LeaveTypeRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Leave-type CRUD. Same pattern as {@code HolidayService}:
 * writes are SUPERADMIN-only; reads are open to any authenticated user but
 * scoped to the caller's company unless the caller is SUPERADMIN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveTypeService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final LeaveTypeRepository leaveTypeRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public LeaveTypeResponse createLeaveType(Long companyId, LeaveTypeRequest request) {
        if (leaveTypeRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Leave type with this name already exists");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        LeaveType leaveType = LeaveType.builder()
                .company(company)
                .name(request.getName())
                .annualQuota(request.getAnnualQuota())
                .isPaid(request.getIsPaid())
                .build();
        leaveType = leaveTypeRepository.save(leaveType);

        log.info("Created leave type id={} name='{}' in company={}", leaveType.getId(), leaveType.getName(), companyId);
        return toResponse(leaveType);
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getLeaveTypes(Long companyId) {
        Long effectiveCompanyId = resolveReadCompanyId(companyId);
        return leaveTypeRepository.findByCompanyId(effectiveCompanyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LeaveTypeResponse updateLeaveType(Long companyId, Long id, LeaveTypeRequest request) {
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));

        if (!leaveType.getName().equalsIgnoreCase(request.getName())
                && leaveTypeRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ConflictException("Leave type with this name already exists");
        }

        leaveType.setName(request.getName());
        leaveType.setAnnualQuota(request.getAnnualQuota());
        leaveType.setIsPaid(request.getIsPaid());
        leaveType = leaveTypeRepository.save(leaveType);

        log.info("Updated leave type id={} name='{}'", leaveType.getId(), leaveType.getName());
        return toResponse(leaveType);
    }

    @Transactional
    public void deleteLeaveType(Long companyId, Long id) {
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        leaveTypeRepository.delete(leaveType);
        log.info("Deleted leave type id={}", id);
    }

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

    private LeaveTypeResponse toResponse(LeaveType leaveType) {
        return LeaveTypeResponse.builder()
                .id(leaveType.getId())
                .name(leaveType.getName())
                .annualQuota(leaveType.getAnnualQuota())
                .isPaid(leaveType.getIsPaid())
                .allowHalfDay(leaveType.getAllowHalfDay())
                .carryForward(leaveType.getCarryForward())
                .maxCarryForwardDays(leaveType.getMaxCarryForwardDays())
                .companyId(leaveType.getCompany() != null ? leaveType.getCompany().getId() : null)
                .createdAt(leaveType.getCreatedAt())
                .build();
    }
}
