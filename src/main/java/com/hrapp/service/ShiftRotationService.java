package com.hrapp.service;

import com.hrapp.util.AppTime;

import com.hrapp.dto.request.AssignShiftRequest;
import com.hrapp.dto.request.ConfirmRotationDraftRequest;
import com.hrapp.dto.request.CreateRotationRequest;
import com.hrapp.dto.request.GenerateRotationDraftRequest;
import com.hrapp.dto.request.RotationMembersRequest;
import com.hrapp.dto.request.UpdateDraftRowRequest;
import com.hrapp.dto.request.UpdateRotationRequest;
import com.hrapp.dto.response.ResolvedShiftResponse;
import com.hrapp.dto.response.RotationDraftResponse;
import com.hrapp.dto.response.RotationDraftRowResponse;
import com.hrapp.dto.response.RotationMemberResponse;
import com.hrapp.dto.response.RotationResponse;
import com.hrapp.dto.response.ShiftResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Shift;
import com.hrapp.entity.ShiftRotation;
import com.hrapp.entity.ShiftRotationDraft;
import com.hrapp.entity.ShiftRotationDraftRow;
import com.hrapp.entity.ShiftRotationMember;
import com.hrapp.entity.ShiftRotationStep;
import com.hrapp.entity.User;
import com.hrapp.enums.RotationDraftStatus;
import com.hrapp.enums.RotationPeriodUnit;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.ShiftRepository;
import com.hrapp.repository.ShiftRotationDraftRepository;
import com.hrapp.repository.ShiftRotationDraftRowRepository;
import com.hrapp.repository.ShiftRotationMemberRepository;
import com.hrapp.repository.ShiftRotationRepository;
import com.hrapp.repository.ShiftRotationStepRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 — rotation definition, membership, and draft → edit → confirm.
 * Confirm writes effective-dated {@code shift_assignments}; drafts never feed attendance resolve.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftRotationService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final ShiftRotationRepository rotationRepository;
    private final ShiftRotationStepRepository stepRepository;
    private final ShiftRotationMemberRepository memberRepository;
    private final ShiftRotationDraftRepository draftRepository;
    private final ShiftRotationDraftRowRepository draftRowRepository;
    private final ShiftRepository shiftRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentService shiftAssignmentService;

    // ── CRUD ───────────────────────────────────────────────────

    @Transactional
    public RotationResponse create(Long companyId, CreateRotationRequest request) {
        Long cid = resolveCompanyId(companyId);
        String name = normalizeName(request.getName());
        validatePeriod(request.getPeriodUnit(), request.getPeriodLength());
        List<Shift> steps = loadActiveShifts(cid, request.getShiftIds());

        if (rotationRepository.existsByCompanyIdAndNameIgnoreCase(cid, name)) {
            throw new ConflictException("Rotation with this name already exists");
        }

        Company company = companyRepository.findById(cid)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        ShiftRotation rotation = rotationRepository.save(ShiftRotation.builder()
                .company(company)
                .name(name)
                .periodUnit(request.getPeriodUnit())
                .periodLength(request.getPeriodLength())
                .isActive(true)
                .build());

        saveSteps(rotation, steps);
        log.info("Created rotation id={} name='{}' company={}", rotation.getId(), name, cid);
        return toRotationResponse(rotation);
    }

    @Transactional(readOnly = true)
    public List<RotationResponse> list(Long companyId, boolean activeOnly) {
        Long cid = resolveCompanyId(companyId);
        List<ShiftRotation> rotations = activeOnly
                ? rotationRepository.findByCompanyIdAndIsActiveTrueOrderByNameAsc(cid)
                : rotationRepository.findByCompanyIdOrderByNameAsc(cid);
        return rotations.stream().map(this::toRotationResponse).toList();
    }

    @Transactional(readOnly = true)
    public RotationResponse get(Long companyId, Long rotationId) {
        return toRotationResponse(requireRotation(companyId, rotationId));
    }

    @Transactional
    public RotationResponse update(Long companyId, Long rotationId, UpdateRotationRequest request) {
        ShiftRotation rotation = requireRotation(companyId, rotationId);

        Long cid = rotation.getCompany().getId();
        String name = normalizeName(request.getName());
        validatePeriod(request.getPeriodUnit(), request.getPeriodLength());
        List<Shift> steps = loadActiveShifts(cid, request.getShiftIds());

        if (rotationRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(cid, name, rotationId)) {
            throw new ConflictException("Rotation with this name already exists");
        }

        rotation.setName(name);
        rotation.setPeriodUnit(request.getPeriodUnit());
        rotation.setPeriodLength(request.getPeriodLength());
        rotationRepository.save(rotation);

        stepRepository.deleteByRotationId(rotationId);
        saveSteps(rotation, steps);

        log.info("Updated rotation id={} company={}", rotationId, cid);
        return toRotationResponse(rotation);
    }

    /**
     * Hard-deletes the rotation. DB cascades steps, members, drafts and draft rows.
     * Applied shift assignments are left untouched.
     */
    @Transactional
    public void delete(Long companyId, Long rotationId) {
        ShiftRotation rotation = requireRotation(companyId, rotationId);
        // Clear open draft rows first so the partial unique index on DRAFT status
        // cannot collide with in-flight persistence state.
        deleteOpenDraftIfPresent(rotationId);
        rotationRepository.delete(rotation);
        rotationRepository.flush();
        log.info("Deleted rotation id={} company={}", rotationId, companyId);
    }

    // ── Members ────────────────────────────────────────────────

    @Transactional
    public List<RotationMemberResponse> addMembers(Long companyId, Long rotationId,
                                                   RotationMembersRequest request) {
        ShiftRotation rotation = requireRotation(companyId, rotationId);
        Long cid = rotation.getCompany().getId();

        for (Long userId : distinctIds(request.getUserIds())) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
            if (user.getCompany() == null || !cid.equals(user.getCompany().getId())) {
                throw new BadRequestException(
                        formatEmployeeLabel(user) + " does not belong to this company");
            }

            var existingOpt = memberRepository.findByUserId(userId);
            if (existingOpt.isPresent()) {
                ShiftRotationMember existing = existingOpt.get();
                ShiftRotation other = existing.getRotation();
                if (other.getId().equals(rotationId)) {
                    continue;
                }
                // Soft-deactivated leftover: free the seat so they can join a live rotation.
                if (!Boolean.TRUE.equals(other.getIsActive())) {
                    memberRepository.deleteMemberById(existing.getId());
                } else {
                    throw new ConflictException(
                            formatEmployeeLabel(user)
                                    + " is already on rotation '"
                                    + other.getName()
                                    + "'");
                }
            }

            memberRepository.save(ShiftRotationMember.builder()
                    .rotation(rotation)
                    .user(user)
                    .build());
        }

        return listMembers(companyId, rotationId);
    }

    @Transactional
    public void removeMember(Long companyId, Long rotationId, Long userId) {
        requireRotation(companyId, rotationId);
        if (memberRepository.findByRotationIdAndUserId(rotationId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Member not found on this rotation");
        }
        memberRepository.deleteByRotationIdAndUserId(rotationId, userId);
        log.info("Removed user={} from rotation={}", userId, rotationId);
    }

    @Transactional(readOnly = true)
    public List<RotationMemberResponse> listMembers(Long companyId, Long rotationId) {
        requireRotation(companyId, rotationId);
        return memberRepository.findByRotationIdOrderByJoinedAtAsc(rotationId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    // ── Draft generate / edit / confirm ────────────────────────

    @Transactional
    public RotationDraftResponse generateDraft(Long companyId, Long rotationId,
                                               GenerateRotationDraftRequest request) {
        ShiftRotation rotation = requireRotation(companyId, rotationId);
        Long cid = rotation.getCompany().getId();

        LocalDate periodStart = resolvePeriodStart(rotation, request != null ? request.getPeriodStart() : null);
        LocalDate periodEnd = resolvePeriodEnd(rotation, periodStart);

        // Replace any open DRAFT first (bulk delete clears the persistence context).
        // Load steps/members AFTER so lazy User/Shift proxies stay usable.
        deleteOpenDraftIfPresent(rotationId);

        List<ShiftRotationStep> steps = stepRepository.findByRotationIdOrderByStepOrderAsc(rotationId);
        if (steps.size() < 2) {
            throw new BadRequestException("Rotation needs at least 2 shifts in the cycle");
        }

        List<ShiftRotationMember> members = memberRepository.findByRotationIdOrderByJoinedAtAsc(rotationId);
        if (members.isEmpty()) {
            throw new BadRequestException("Add members before generating a draft");
        }

        ShiftRotationDraft draft = draftRepository.save(ShiftRotationDraft.builder()
                .rotation(rotation)
                .company(rotation.getCompany())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(RotationDraftStatus.DRAFT)
                .build());

        List<Shift> cycle = steps.stream().map(ShiftRotationStep::getShift).toList();
        for (ShiftRotationMember member : members) {
            User user = member.getUser();
            ResolvedShiftResponse resolved = shiftAssignmentService.resolveForSystem(
                    cid, user.getId(), periodStart);
            Shift current = null;
            if (resolved.getShift() != null && resolved.getShift().getId() != null) {
                current = shiftRepository.findById(resolved.getShift().getId()).orElse(null);
            }
            Shift proposed = nextInCycle(cycle, current);

            draftRowRepository.save(ShiftRotationDraftRow.builder()
                    .draft(draft)
                    .user(user)
                    .currentShift(current)
                    .proposedShift(proposed)
                    .build());
        }

        log.info("Generated draft id={} rotation={} period={}..{}",
                draft.getId(), rotationId, periodStart, periodEnd);
        return toDraftResponse(draft);
    }

    @Transactional
    public void deleteCurrentDraft(Long companyId, Long rotationId) {
        requireRotation(companyId, rotationId);
        ShiftRotationDraft open = draftRepository
                .findByRotationIdAndStatus(rotationId, RotationDraftStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("No open draft for this rotation"));
        deleteDraftFully(open.getId());
        log.info("Deleted open draft id={} for rotation={}", open.getId(), rotationId);
    }

    @Transactional(readOnly = true)
    public RotationDraftResponse getCurrentDraft(Long companyId, Long rotationId) {
        requireRotation(companyId, rotationId);
        ShiftRotationDraft draft = draftRepository
                .findByRotationIdAndStatus(rotationId, RotationDraftStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("No open draft for this rotation"));
        return toDraftResponse(draft);
    }

    @Transactional
    public RotationDraftResponse updateDraftRow(Long companyId, Long rotationId, Long userId,
                                                UpdateDraftRowRequest request) {
        ShiftRotation rotation = requireRotation(companyId, rotationId);
        ShiftRotationDraft draft = draftRepository
                .findByRotationIdAndStatus(rotationId, RotationDraftStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("No open draft for this rotation"));

        ShiftRotationDraftRow row = draftRowRepository.findByDraftIdAndUserId(draft.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft row not found for employee"));

        Shift proposed = shiftRepository
                .findByIdAndCompanyId(request.getProposedShiftId(), rotation.getCompany().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (!Boolean.TRUE.equals(proposed.getIsActive())) {
            throw new BadRequestException("Cannot propose an inactive shift");
        }

        row.setProposedShift(proposed);
        draftRowRepository.save(row);
        log.info("Updated draft row draft={} user={} proposedShift={}",
                draft.getId(), userId, proposed.getId());
        return toDraftResponse(draft);
    }

    @Transactional
    public RotationDraftResponse confirmDraft(Long companyId, Long rotationId,
                                              ConfirmRotationDraftRequest request) {
        requireRotation(companyId, rotationId);
        ShiftRotationDraft draft = draftRepository
                .findByRotationIdAndStatus(rotationId, RotationDraftStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("No open draft for this rotation"));

        LocalDate effectiveFrom = request.getEffectiveFrom();
        LocalDate periodEnd = draft.getPeriodEnd();
        if (periodEnd != null && periodEnd.isBefore(effectiveFrom)) {
            throw new BadRequestException(
                    "effectiveFrom cannot be after the draft period end (" + periodEnd + ")");
        }

        List<ShiftRotationDraftRow> rows = draftRowRepository.findByDraftIdOrderByUser_FullNameAsc(draft.getId());
        if (rows.isEmpty()) {
            throw new BadRequestException("Draft has no rows to confirm");
        }

        // Cover = [effectiveFrom, periodEnd]: clear only overlapping later assigns
        // inside the window (e.g. Morning from 30 Nov). Rows after periodEnd stay
        // (e.g. Morning from 15 Dec). Assignment itself stays open-ended so Night
        // continues after periodEnd until a later assign wins.
        LocalDate clearThrough = periodEnd != null ? periodEnd : effectiveFrom;

        for (ShiftRotationDraftRow row : rows) {
            shiftAssignmentService.assignShift(companyId, AssignShiftRequest.builder()
                    .userId(row.getUser().getId())
                    .shiftId(row.getProposedShift().getId())
                    .effectiveFrom(effectiveFrom)
                    .build(), clearThrough);
        }

        Long actorId = SecurityUtil.getCurrentUserId();
        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        draft.setStatus(RotationDraftStatus.CONFIRMED);
        draft.setConfirmedAt(AppTime.now());
        draft.setConfirmedBy(actor);
        draftRepository.save(draft);

        log.info("Confirmed draft id={} rotation={} effectiveFrom={} rows={}",
                draft.getId(), rotationId, effectiveFrom, rows.size());
        return toDraftResponse(draft);
    }

    // ── helpers ────────────────────────────────────────────────

    private LocalDate resolvePeriodStart(ShiftRotation rotation, LocalDate requested) {
        LocalDate start;
        if (requested != null) {
            start = requested;
        } else {
            start = draftRepository
                    .findFirstByRotationIdAndStatusOrderByPeriodEndDesc(
                            rotation.getId(), RotationDraftStatus.CONFIRMED)
                    .map(d -> d.getPeriodEnd().plusDays(1))
                    .orElse(AppTime.today());
        }
        if (rotation.getPeriodUnit() == RotationPeriodUnit.WEEKLY) {
            return start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return start;
    }

    private LocalDate resolvePeriodEnd(ShiftRotation rotation, LocalDate periodStart) {
        if (rotation.getPeriodUnit() == RotationPeriodUnit.WEEKLY) {
            int weeks = Math.max(rotation.getPeriodLength(), 1);
            return periodStart.plusWeeks(weeks).minusDays(1);
        }
        int days = Math.max(rotation.getPeriodLength(), 1);
        return periodStart.plusDays(days - 1L);
    }

    private Shift nextInCycle(List<Shift> cycle, Shift current) {
        if (current == null || current.getId() == null) {
            return cycle.get(0);
        }
        for (int i = 0; i < cycle.size(); i++) {
            if (current.getId().equals(cycle.get(i).getId())) {
                return cycle.get((i + 1) % cycle.size());
            }
        }
        return cycle.get(0);
    }

    private List<Shift> loadActiveShifts(Long companyId, List<Long> shiftIds) {
        List<Long> ids = distinctIds(shiftIds);
        if (ids.size() < 2) {
            throw new BadRequestException("Rotation cycle requires at least 2 shifts");
        }
        List<Shift> shifts = new ArrayList<>();
        for (Long shiftId : ids) {
            Shift shift = shiftRepository.findByIdAndCompanyId(shiftId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));
            if (!Boolean.TRUE.equals(shift.getIsActive())) {
                throw new BadRequestException("Shift " + shiftId + " is inactive");
            }
            shifts.add(shift);
        }
        return shifts;
    }

    private void saveSteps(ShiftRotation rotation, List<Shift> shifts) {
        for (int i = 0; i < shifts.size(); i++) {
            stepRepository.save(ShiftRotationStep.builder()
                    .rotation(rotation)
                    .shift(shifts.get(i))
                    .stepOrder(i)
                    .build());
        }
    }

    private void validatePeriod(RotationPeriodUnit unit, Integer length) {
        if (unit == null) {
            throw new BadRequestException("periodUnit is required");
        }
        if (length == null || length < 1) {
            throw new BadRequestException("periodLength must be >= 1");
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                seen.add(id);
            }
        }
        return new ArrayList<>(seen);
    }

    private ShiftRotation requireRotation(Long companyId, Long rotationId) {
        Long cid = resolveCompanyId(companyId);
        return rotationRepository.findByIdAndCompanyId(rotationId, cid)
                .orElseThrow(() -> new ResourceNotFoundException("Rotation not found"));
    }

    private void deleteOpenDraftIfPresent(Long rotationId) {
        draftRepository.findByRotationIdAndStatus(rotationId, RotationDraftStatus.DRAFT)
                .ifPresent(existing -> deleteDraftFully(existing.getId()));
    }

    private void deleteDraftFully(Long draftId) {
        draftRowRepository.deleteByDraftId(draftId);
        draftRepository.deleteDraftById(draftId);
    }

    private String formatEmployeeLabel(User user) {
        String name = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName().trim()
                : "Employee";
        String code = user.getEmpCode();
        if (code != null && !code.isBlank()) {
            return name + " (" + code.trim() + ")";
        }
        return name;
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

    private RotationResponse toRotationResponse(ShiftRotation rotation) {
        List<ShiftResponse> steps = stepRepository
                .findByRotationIdOrderByStepOrderAsc(rotation.getId()).stream()
                .map(s -> toShiftResponse(s.getShift()))
                .toList();
        int memberCount = (int) memberRepository.countByRotationId(rotation.getId());
        return RotationResponse.builder()
                .id(rotation.getId())
                .companyId(rotation.getCompany() != null ? rotation.getCompany().getId() : null)
                .name(rotation.getName())
                .periodUnit(rotation.getPeriodUnit() != null ? rotation.getPeriodUnit().name() : null)
                .periodLength(rotation.getPeriodLength())
                .isActive(rotation.getIsActive())
                .steps(steps)
                .memberCount(memberCount)
                .createdAt(rotation.getCreatedAt())
                .updatedAt(rotation.getUpdatedAt())
                .build();
    }

    private RotationMemberResponse toMemberResponse(ShiftRotationMember member) {
        User user = member.getUser();
        return RotationMemberResponse.builder()
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private RotationDraftResponse toDraftResponse(ShiftRotationDraft draft) {
        List<RotationDraftRowResponse> rows = draftRowRepository
                .findByDraftIdOrderByUser_FullNameAsc(draft.getId()).stream()
                .map(this::toDraftRowResponse)
                .toList();
        ShiftRotation rotation = draft.getRotation();
        return RotationDraftResponse.builder()
                .id(draft.getId())
                .rotationId(rotation != null ? rotation.getId() : null)
                .rotationName(rotation != null ? rotation.getName() : null)
                .companyId(draft.getCompany() != null ? draft.getCompany().getId() : null)
                .periodStart(draft.getPeriodStart())
                .periodEnd(draft.getPeriodEnd())
                .status(draft.getStatus() != null ? draft.getStatus().name() : null)
                .confirmedAt(draft.getConfirmedAt())
                .rows(rows)
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private RotationDraftRowResponse toDraftRowResponse(ShiftRotationDraftRow row) {
        User user = row.getUser();
        Shift current = row.getCurrentShift();
        Shift proposed = row.getProposedShift();
        return RotationDraftRowResponse.builder()
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .currentShiftId(current != null ? current.getId() : null)
                .currentShiftName(current != null ? current.getName() : null)
                .proposedShiftId(proposed != null ? proposed.getId() : null)
                .proposedShiftName(proposed != null ? proposed.getName() : null)
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
