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
import com.hrapp.repository.AdvanceJdbcRepository;
import com.hrapp.repository.AdvanceRepository;
import com.hrapp.repository.AdvanceRow;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Salary-advance workflow: employees request, ADMIN/HR approve or reject,
 * employees can cancel while still PENDING. List reads use
 * {@link AdvanceJdbcRepository}; writes stay on JPA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CANCEL_REASON = "Cancelled by employee";

    private final AdvanceRepository advanceRepository;
    private final AdvanceJdbcRepository advanceJdbcRepository;
    private final UserRepository userRepository;

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
        return toAdvanceResponse(advance);
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> getMyAdvances() {
        Long userId = requireCallerUserId();
        return advanceJdbcRepository.getAdvancesByUser(userId).stream()
                .map(this::toAdvanceResponse)
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
        return toAdvanceResponse(advance);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> getPendingAdvances(int page, int size) {
        validateAdvanceListPage(page, size);
        Long companyId = requireCallerCompanyId();
        Long callerId = requireCallerUserId();
        long offset = (long) page * size;
        List<AdvanceRow> rows = advanceJdbcRepository
                .getPendingAdvancesByCompanyExcludingUser(companyId, callerId, size, offset);
        long total = advanceJdbcRepository
                .getPendingAdvancesByCompanyExcludingUserCount(companyId, callerId);
        return PageResponse.of(rows.stream().map(this::toAdvanceResponse).toList(), page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> getCompanyAdvances(
            AdvanceStatus status, Long employeeId, int page, int size) {
        validateAdvanceListPage(page, size);
        Long companyId = requireCallerCompanyId();
        String statusName = status != null ? status.name() : null;
        long offset = (long) page * size;
        List<AdvanceRow> rows = advanceJdbcRepository
                .getCompanyAdvancesFiltered(companyId, statusName, employeeId, size, offset);
        long total = advanceJdbcRepository
                .getCompanyAdvancesFilteredCount(companyId, statusName, employeeId);
        return PageResponse.of(rows.stream().map(this::toAdvanceResponse).toList(), page, size, total);
    }

    @Transactional
    public AdvanceResponse actionAdvance(Long id, ActionAdvanceRequest request) {
        Long companyId = requireCallerCompanyId();
        Long actionerId = requireCallerUserId();

        Advance advance = advanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        ensureEmployeeBelongsToCallerCompany(advance.getUser(), companyId);

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
        return toAdvanceResponse(advance);
    }

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

    private void ensureEmployeeBelongsToCallerCompany(User user, Long callerCompanyId) {
        Long userCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (!callerCompanyId.equals(userCompanyId)) {
            log.warn("Cross-company advance access blocked — caller={} target={}",
                    callerCompanyId, userCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
    }

    private static void validateAdvanceListPage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private AdvanceResponse toAdvanceResponse(Advance advance) {
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

    private AdvanceResponse toAdvanceResponse(AdvanceRow row) {
        return AdvanceResponse.builder()
                .id(row.id())
                .userId(row.userId())
                .fullName(row.fullName())
                .empCode(row.empCode())
                .amount(row.amount())
                .reason(row.reason())
                .rejectReason(row.rejectReason())
                .status(row.status())
                .deductFromMonth(row.deductFromMonth())
                .deductFromYear(row.deductFromYear())
                .isRecovered(row.isRecovered())
                .approvedByName(row.approvedByName())
                .createdAt(row.createdAt())
                .actionedAt(row.actionedAt())
                .build();
    }
}
