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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Batch resolve for one user across many dates — 2 DB round-trips total
     * (assignment history + company default), then in-memory lookup per date.
     * Used by attendance history to avoid N× resolve on Supabase.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, ResolvedShiftResponse> resolveForSystemMany(
            Long companyId, Long userId, Collection<LocalDate> dates) {
        Map<LocalDate, ResolvedShiftResponse> result = new HashMap<>();
        if (dates == null || dates.isEmpty()) {
            return result;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (user.getCompany() == null || !companyId.equals(user.getCompany().getId())) {
            throw new UnauthorizedException("Employee does not belong to this company");
        }
        List<ShiftAssignment> history = shiftAssignmentRepository.findHistoryByUserId(userId);
        Shift companyDefault = shiftRepository
                .findByCompanyIdAndIsDefaultTrueAndIsActiveTrue(companyId)
                .orElse(null);
        for (LocalDate onDate : dates) {
            if (onDate != null) {
                result.put(onDate, resolveFromLoaded(user, onDate, history, companyDefault));
            }
        }
        return result;
    }

    /**
     * Batch resolve for many users on one date — 2 DB round-trips
     * (assignments for those users + company default).
     */
    @Transactional(readOnly = true)
    public Map<Long, ResolvedShiftResponse> resolveForUsersOnDate(
            Long companyId, Collection<Long> userIds, LocalDate onDate) {
        Map<Long, ResolvedShiftResponse> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty() || onDate == null) {
            return result;
        }
        Shift companyDefault = shiftRepository
                .findByCompanyIdAndIsDefaultTrueAndIsActiveTrue(companyId)
                .orElse(null);
        List<ShiftAssignment> candidates = shiftAssignmentRepository
                .findEffectiveOnOrBeforeForUsers(userIds, onDate);
        Map<Long, ShiftAssignment> effectiveByUser = new HashMap<>();
        for (ShiftAssignment sa : candidates) {
            Long uid = sa.getUser().getId();
            // Ordered DESC by effectiveFrom — first win per user
            effectiveByUser.putIfAbsent(uid, sa);
        }
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            User stub = User.builder().id(userId).build();
            result.put(userId, resolveFromAssignmentAndDefault(
                    stub, onDate, effectiveByUser.get(userId), companyDefault));
        }
        return result;
    }

    private ResolvedShiftResponse resolveInternal(User user, Long companyId, LocalDate onDate) {
        var assignmentOpt = shiftAssignmentRepository
                .findFirstByUser_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user.getId(), onDate);
        ShiftAssignment sa = assignmentOpt.orElse(null);
        Shift companyDefault = shiftRepository
                .findByCompanyIdAndIsDefaultTrueAndIsActiveTrue(companyId)
                .orElse(null);
        return resolveFromAssignmentAndDefault(user, onDate, sa, companyDefault);
    }

    private ResolvedShiftResponse resolveFromLoaded(User user, LocalDate onDate,
                                                    List<ShiftAssignment> historyDesc,
                                                    Shift companyDefault) {
        ShiftAssignment effective = null;
        for (ShiftAssignment sa : historyDesc) {
            if (!sa.getEffectiveFrom().isAfter(onDate)) {
                effective = sa;
                break;
            }
        }
        return resolveFromAssignmentAndDefault(user, onDate, effective, companyDefault);
    }

    private ResolvedShiftResponse resolveFromAssignmentAndDefault(
            User user, LocalDate onDate, ShiftAssignment sa, Shift companyDefault) {
        if (sa != null) {
            Shift shift = sa.getShift();
            if (shift != null && Boolean.TRUE.equals(shift.getIsActive())) {
                return ResolvedShiftResponse.builder()
                        .userId(user.getId())
                        .date(onDate)
                        .source(ShiftResolveSource.ASSIGNMENT)
                        .shift(toShiftResponse(shift))
                        .assignmentId(sa.getId())
                        .assignmentEffectiveFrom(sa.getEffectiveFrom())
                        .build();
            }
            log.warn("Effective assignment id={} points at inactive shift — falling back",
                    sa.getId());
        }

        if (companyDefault != null) {
            return ResolvedShiftResponse.builder()
                    .userId(user.getId())
                    .date(onDate)
                    .source(ShiftResolveSource.COMPANY_DEFAULT)
                    .shift(toShiftResponse(companyDefault))
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
