package com.hrapp.service;

import com.hrapp.dto.request.LeaveTypeRequest;
import com.hrapp.dto.response.LeaveTypeResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.LeaveType;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.LeaveBalanceRepository;
import com.hrapp.repository.LeaveTypeRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Leave-type CRUD. SUPERADMIN can manage any company; ADMIN is locked to their
 * own company regardless of the path {@code companyId}. Reads are open to any
 * authenticated user but similarly scoped for non-SUPERADMIN callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveTypeService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public LeaveTypeResponse createLeaveType(Long companyId, LeaveTypeRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        if (leaveTypeRepository.existsByCompanyIdAndName(effectiveCompanyId, request.getName())) {
            throw new ConflictException("Leave type with this name already exists");
        }

        Company company = companyRepository.findById(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        boolean allowHalfDay = Boolean.TRUE.equals(request.getAllowHalfDay());
        boolean carryForward = Boolean.TRUE.equals(request.getCarryForward());
        int maxCarry = resolveMaxCarryForwardDays(carryForward, request.getMaxCarryForwardDays());

        LeaveType leaveType = LeaveType.builder()
                .company(company)
                .name(request.getName())
                .annualQuota(request.getAnnualQuota())
                .isPaid(request.getIsPaid())
                .allowHalfDay(allowHalfDay)
                .carryForward(carryForward)
                .maxCarryForwardDays(maxCarry)
                .build();
        leaveType = leaveTypeRepository.save(leaveType);

        log.info("Created leave type id={} name='{}' in company={}",
                leaveType.getId(), leaveType.getName(), effectiveCompanyId);
        return toResponse(leaveType);
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getLeaveTypes(Long companyId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        return leaveTypeRepository.findByCompanyId(effectiveCompanyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LeaveTypeResponse updateLeaveType(Long companyId, Long id, LeaveTypeRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));

        if (!leaveType.getName().equalsIgnoreCase(request.getName())
                && leaveTypeRepository.existsByCompanyIdAndName(effectiveCompanyId, request.getName())) {
            throw new ConflictException("Leave type with this name already exists");
        }

        boolean allowHalfDay = Boolean.TRUE.equals(request.getAllowHalfDay());
        boolean carryForward = Boolean.TRUE.equals(request.getCarryForward());
        int maxCarry = resolveMaxCarryForwardDays(carryForward, request.getMaxCarryForwardDays());

        // annualQuota changes apply to *future* balance seeds only — existing
        // leave_balances rows are left untouched (bug 8.3).
        leaveType.setName(request.getName());
        leaveType.setAnnualQuota(request.getAnnualQuota());
        leaveType.setIsPaid(request.getIsPaid());
        leaveType.setAllowHalfDay(allowHalfDay);
        leaveType.setCarryForward(carryForward);
        leaveType.setMaxCarryForwardDays(maxCarry);
        leaveType = leaveTypeRepository.save(leaveType);

        log.info("Updated leave type id={} name='{}' (existing employee balances not rewritten)",
                leaveType.getId(), leaveType.getName());
        return toResponse(leaveType);
    }

    @Transactional
    public void deleteLeaveType(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));

        long balanceCount = leaveBalanceRepository.countByLeaveTypeId(id);
        if (balanceCount > 0) {
            throw new ConflictException(
                    "Cannot delete leave type: " + balanceCount
                            + " employee balance(s) exist. Deactivate or reassign balances instead.");
        }

        leaveTypeRepository.delete(leaveType);
        log.info("Deleted leave type id={}", id);
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

    private int resolveMaxCarryForwardDays(boolean carryForward, Integer requested) {
        if (!carryForward) {
            return 0;
        }
        return requested != null ? requested : 0;
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
