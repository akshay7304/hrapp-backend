package com.hrapp.service;

import com.hrapp.util.AppTime;

import com.hrapp.dto.request.AssignShiftRequest;
import com.hrapp.dto.response.ResolvedShiftResponse;
import com.hrapp.dto.response.ShiftAssignmentResponse;
import com.hrapp.dto.response.ShiftResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Shift;
import com.hrapp.entity.ShiftAssignment;
import com.hrapp.entity.User;
import com.hrapp.enums.ShiftResolveSource;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.ShiftAssignmentRepository;
import com.hrapp.repository.ShiftRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Fixed shift assignment with effective dating, plus resolve-for-date
 * (assignment → company default → NONE). Company settings are not a shift source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftAssignmentService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public ShiftAssignmentResponse assignShift(Long companyId, AssignShiftRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Long actorId = SecurityUtil.getCurrentUserId();

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureUserInCompany(user, effectiveCompanyId);

        Shift shift = shiftRepository.findByIdAndCompanyId(request.getShiftId(), effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (!Boolean.TRUE.equals(shift.getIsActive())) {
            throw new BadRequestException("Cannot assign an inactive shift");
        }

        LocalDate effectiveFrom = request.getEffectiveFrom();
        ShiftAssignment assignment = shiftAssignmentRepository
                .findByUser_IdAndEffectiveFrom(user.getId(), effectiveFrom)
                .orElse(null);

        User actor = actorId != null
                ? userRepository.findById(actorId).orElse(null)
                : null;

        if (assignment == null) {
            Company company = companyRepository.findById(effectiveCompanyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
            assignment = ShiftAssignment.builder()
                    .company(company)
                    .user(user)
                    .shift(shift)
                    .effectiveFrom(effectiveFrom)
                    .createdBy(actor)
                    .build();
        } else {
            assignment.setShift(shift);
            if (actor != null) {
                assignment.setCreatedBy(actor);
            }
        }

        assignment = shiftAssignmentRepository.save(assignment);
        log.info("Assigned shift id={} to user={} effectiveFrom={} company={}",
                shift.getId(), user.getId(), effectiveFrom, effectiveCompanyId);
        return toAssignmentResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> getAssignmentHistory(Long companyId, Long userId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureUserInCompany(user, effectiveCompanyId);
        ensureCanViewUser(userId);

        return shiftAssignmentRepository.findHistoryByUserId(userId).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResolvedShiftResponse resolveForUser(Long companyId, Long userId, LocalDate date) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureUserInCompany(user, effectiveCompanyId);
        ensureCanViewUser(userId);

        LocalDate onDate = date != null ? date : AppTime.today();
        return resolveInternal(user, effectiveCompanyId, onDate);
    }

    @Transactional(readOnly = true)
    public ResolvedShiftResponse resolveMine(LocalDate date) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate onDate = date != null ? date : AppTime.today();
        return resolveInternal(user, companyId, onDate);
    }

    /**
     * Trusted internal resolve for attendance / thumb / schedulers — no
     * caller-permission check (the calling service already scoped the tenant).
     */
    @Transactional(readOnly = true)
    public ResolvedShiftResponse resolveForSystem(Long companyId, Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (user.getCompany() == null || !companyId.equals(user.getCompany().getId())) {
            throw new UnauthorizedException("Employee does not belong to this company");
        }
        LocalDate onDate = date != null ? date : AppTime.today();
        return resolveInternal(user, companyId, onDate);
    }

    private ResolvedShiftResponse resolveInternal(User user, Long companyId, LocalDate onDate) {
        var assignmentOpt = shiftAssignmentRepository
                .findFirstByUser_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user.getId(), onDate);
        if (assignmentOpt.isPresent()) {
            ShiftAssignment sa = assignmentOpt.get();
            Shift shift = sa.getShift();
            if (Boolean.TRUE.equals(shift.getIsActive())) {
                return ResolvedShiftResponse.builder()
                        .userId(user.getId())
                        .date(onDate)
                        .source(ShiftResolveSource.ASSIGNMENT)
                        .shift(toShiftResponse(shift))
                        .assignmentId(sa.getId())
                        .assignmentEffectiveFrom(sa.getEffectiveFrom())
                        .build();
            }
            // Assignment points at deactivated shift — fall through to company default
            log.warn("Effective assignment id={} points at inactive shift id={} — falling back",
                    sa.getId(), shift.getId());
        }

        var defaultOpt = shiftRepository.findByCompanyIdAndIsDefaultTrueAndIsActiveTrue(companyId);
        if (defaultOpt.isPresent()) {
            return ResolvedShiftResponse.builder()
                    .userId(user.getId())
                    .date(onDate)
                    .source(ShiftResolveSource.COMPANY_DEFAULT)
                    .shift(toShiftResponse(defaultOpt.get()))
                    .build();
        }

        return ResolvedShiftResponse.builder()
                .userId(user.getId())
                .date(onDate)
                .source(ShiftResolveSource.NONE)
                .shift(null)
                .build();
    }

    private void ensureCanViewUser(Long targetUserId) {
        // EMPLOYEE (without higher role) may only see self. ADMIN/HR/SA via hierarchy.
        if (SecurityUtil.hasRole("ADMIN") || SecurityUtil.hasRole("HR")
                || SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            return;
        }
        Long me = SecurityUtil.getCurrentUserId();
        if (me == null || !me.equals(targetUserId)) {
            throw new UnauthorizedException("You can only view your own shift");
        }
    }

    private void ensureUserInCompany(User user, Long companyId) {
        if (user.getCompany() == null || !companyId.equals(user.getCompany().getId())) {
            throw new UnauthorizedException("Employee does not belong to this company");
        }
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

    private ShiftAssignmentResponse toAssignmentResponse(ShiftAssignment sa) {
        User user = sa.getUser();
        Shift shift = sa.getShift();
        User createdBy = sa.getCreatedBy();
        return ShiftAssignmentResponse.builder()
                .id(sa.getId())
                .companyId(sa.getCompany() != null ? sa.getCompany().getId() : null)
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .shiftId(shift != null ? shift.getId() : null)
                .shiftName(shift != null ? shift.getName() : null)
                .effectiveFrom(sa.getEffectiveFrom())
                .createdById(createdBy != null ? createdBy.getId() : null)
                .createdByName(createdBy != null ? createdBy.getFullName() : null)
                .createdAt(sa.getCreatedAt())
                .build();
    }

    private ShiftResponse toShiftResponse(Shift shift) {
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
