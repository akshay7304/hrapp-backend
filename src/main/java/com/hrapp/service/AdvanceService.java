package com.hrapp.service;

import com.hrapp.util.AppTime;

import com.hrapp.dto.request.ActionAdvanceRequest;
import com.hrapp.dto.request.AdvanceRequest;
import com.hrapp.dto.response.AdvanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.entity.Advance;
import com.hrapp.entity.User;
import com.hrapp.enums.AdvanceStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AdvanceRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salary-advance workflow: employees request, ADMIN/HR approve or reject,
 * employees can cancel while still PENDING. Multi-tenancy is enforced at
 * every entry point via {@link SecurityUtil}.
 *
 * Reject/cancel store text in {@code reject_reason}; the requester's original
 * {@code reason} is preserved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceService {

    private static final String CANCEL_REASON = "Cancelled by employee";

    private final AdvanceRepository advanceRepository;
    private final UserRepository userRepository;

    // ============================================================
    //  Employee actions
    // ============================================================

    @Transactional
    public AdvanceResponse requestAdvance(AdvanceRequest request) {
        Long userId = requireCallerUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean hasPending = !advanceRepository
                .findByUserIdAndStatus(userId, AdvanceStatus.PENDING)
                .isEmpty();
        if (hasPending) {
            throw new ConflictException("You already have a pending advance request");
        }

        Advance advance = Advance.builder()
                .user(user)
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(AdvanceStatus.PENDING)
                .isRecovered(false)
                .build();
        advance = advanceRepository.save(advance);
        log.info("Advance requested id={} user={} amount={}",
                advance.getId(), userId, request.getAmount());
        return toResponse(advance);
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> getMyAdvances() {
        Long userId = requireCallerUserId();
        return advanceRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdvanceResponse cancelAdvance(Long id) {
        Long userId = requireCallerUserId();
        Advance advance = advanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        if (!advance.getUser().getId().equals(userId)) {
            log.warn("Advance cancel blocked — caller={} target user={}", userId, advance.getUser().getId());
            throw new UnauthorizedException("You can only cancel your own advances");
        }
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw new BadRequestException("Only pending advances can be cancelled");
        }

        advance.setStatus(AdvanceStatus.REJECTED);
        advance.setRejectReason(CANCEL_REASON);
        advance.setActionedAt(AppTime.now());
        advance = advanceRepository.save(advance);
        log.info("Advance id={} cancelled by user={}", id, userId);
        return toResponse(advance);
    }

    // ============================================================
    //  Admin / HR actions
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> getPendingAdvances(Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Long callerId = requireCallerUserId();
        // Newest first; exclude caller's own pending (self-action already blocked).
        Pageable effective = applyDefaultSort(
                pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(advanceRepository
                .findByUser_CompanyIdAndStatusAndUser_IdNot(
                        companyId, AdvanceStatus.PENDING, callerId, effective)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> getCompanyAdvances(
            AdvanceStatus status, Long employeeId, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Pageable effective = applyDefaultSort(
                pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(advanceRepository
                .findCompanyFiltered(companyId, status, employeeId, effective)
                .map(this::toResponse));
    }

    private Pageable applyDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    @Transactional
    public AdvanceResponse actionAdvance(Long id, ActionAdvanceRequest request) {
        Long companyId = requireCallerCompanyId();
        Long actionerId = requireCallerUserId();

        Advance advance = advanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        ensureSameCompany(advance.getUser(), companyId);

        if (advance.getUser().getId().equals(actionerId)) {
            throw new BadRequestException(
                    "You cannot approve or reject your own advance request. "
                            + "Please ask another HR or Admin to action it.");
        }

        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw new BadRequestException("Advance is not pending");
        }
        if (request.getStatus() != AdvanceStatus.APPROVED
                && request.getStatus() != AdvanceStatus.REJECTED) {
            throw new BadRequestException("Status must be APPROVED or REJECTED");
        }

        if (request.getStatus() == AdvanceStatus.APPROVED) {
            if (request.getDeductFromMonth() == null) {
                throw new BadRequestException("Deduct from month is required when approving");
            }
            if (request.getDeductFromYear() == null) {
                throw new BadRequestException("Deduct from year is required when approving");
            }
            User actioner = userRepository.findById(actionerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approving user not found"));
            advance.setDeductFromMonth(request.getDeductFromMonth());
            advance.setDeductFromYear(request.getDeductFromYear());
            advance.setApprovedBy(actioner);
        } else {
            if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
                throw new BadRequestException("Reject reason is required when rejecting an advance");
            }
            advance.setRejectReason(request.getRejectReason());
        }

        advance.setStatus(request.getStatus());
        advance.setActionedAt(AppTime.now());
        advance = advanceRepository.save(advance);
        log.info("Advance id={} {} by user={}", id, request.getStatus(), actionerId);
        return toResponse(advance);
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private Long requireCallerUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private void ensureSameCompany(User user, Long callerCompanyId) {
        Long userCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (!callerCompanyId.equals(userCompanyId)) {
            log.warn("Cross-company advance access blocked — caller={} target={}",
                    callerCompanyId, userCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
    }

    private AdvanceResponse toResponse(Advance advance) {
        User user = advance.getUser();
        User approver = advance.getApprovedBy();
        return AdvanceResponse.builder()
                .id(advance.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .amount(advance.getAmount())
                .reason(advance.getReason())
                .rejectReason(advance.getRejectReason())
                .status(advance.getStatus() != null ? advance.getStatus().name() : null)
                .deductFromMonth(advance.getDeductFromMonth())
                .deductFromYear(advance.getDeductFromYear())
                .isRecovered(advance.getIsRecovered())
                .approvedByName(approver != null ? approver.getFullName() : null)
                .createdAt(advance.getCreatedAt())
                .actionedAt(advance.getActionedAt())
                .build();
    }
}
