package com.hrapp.service;

import com.hrapp.dto.response.AdvanceReportResponse;
import com.hrapp.dto.response.AdvanceResponse;
import com.hrapp.dto.response.AttendanceReportResponse;
import com.hrapp.dto.response.LeaveReportResponse;
import com.hrapp.dto.response.LeaveRequestResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryReportResponse;
import com.hrapp.entity.Advance;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.Holiday;
import com.hrapp.entity.LeaveRequest;
import com.hrapp.entity.SalaryAdjustment;
import com.hrapp.entity.SalaryPayment;
import com.hrapp.entity.SalaryStructure;
import com.hrapp.entity.User;
import com.hrapp.enums.AdvanceStatus;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.LeaveRequestStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AdvanceRepository;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveBalanceRepository;
import com.hrapp.repository.LeaveRequestRepository;
import com.hrapp.repository.SalaryAdjustmentRepository;
import com.hrapp.repository.SalaryPaymentRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JSON reporting endpoints for HR/Admin dashboards. Every report is scoped to
 * the caller's company via {@link SecurityUtil}.
 *
 * <p>Performance notes:
 * <ul>
 *   <li>Each report does a single user fetch via {@code findByCompanyId}, which
 *       already {@code JOIN FETCH}es department, designation and status.</li>
 *   <li>Attendance / leave / advance / adjustment lookups are batched by
 *       company and grouped by user id rather than queried per-employee.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String ADJ_TYPE_ADDITION = "ADDITION";
    private static final String ADJ_TYPE_DEDUCTION = "DEDUCTION";
    private static final List<String> ATTENDANCE_EXCLUDED_STATUSES =
            List.of("Terminated", "Resigned");

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final SalaryAdjustmentRepository salaryAdjustmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    @SuppressWarnings("unused") // injected per spec; kept for future per-employee balance breakdowns
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AdvanceRepository advanceRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsRepository companySettingsRepository;

    // ============================================================
    //  Attendance report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<AttendanceReportResponse> getMonthlyAttendanceReport(
            Integer month, Integer year, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        Integer totalWorkingDays = calculateWorkingDays(monthStart, monthEnd, settings, companyId);

        List<User> employees = userRepository
                .findByCompanyIdAndStatus_NameNotIn(companyId, ATTENDANCE_EXCLUDED_STATUSES);

        List<AttendanceReportResponse> all = employees.stream()
                .map(employee -> buildAttendanceReport(
                        employee, month, year, monthStart, monthEnd, totalWorkingDays))
                .sorted(byEmpCode(AttendanceReportResponse::getEmpCode))
                .toList();
        return sliceInMemory(all, pageable);
    }

    private AttendanceReportResponse buildAttendanceReport(User employee, Integer month, Integer year,
                                                           LocalDate monthStart, LocalDate monthEnd,
                                                           Integer totalWorkingDays) {
        List<Attendance> records = attendanceRepository
                .findByUserIdAndAttendanceDateBetween(employee.getId(), monthStart, monthEnd);
        Map<LocalDate, Boolean> paidLeaveMap =
                buildLeavePaidMap(employee.getId(), monthStart, monthEnd);

        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal halfDays = BigDecimal.ZERO;
        BigDecimal onLeaveDays = BigDecimal.ZERO;
        BigDecimal paidLeaveDays = BigDecimal.ZERO;
        BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
        int holidayDays = 0;
        int weekOffDays = 0;
        BigDecimal overtimeHours = BigDecimal.ZERO;

        for (Attendance a : records) {
            overtimeHours = overtimeHours.add(nullSafe(a.getOvertimeHours()));
            AttendanceStatus status = a.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case PRESENT -> presentDays = presentDays.add(BigDecimal.ONE);
                case ABSENT -> absentDays = absentDays.add(BigDecimal.ONE);
                case HALF_DAY -> halfDays = halfDays.add(BigDecimal.ONE);
                case ON_LEAVE -> {
                    onLeaveDays = onLeaveDays.add(BigDecimal.ONE);
                    if (Boolean.TRUE.equals(paidLeaveMap.get(a.getAttendanceDate()))) {
                        paidLeaveDays = paidLeaveDays.add(BigDecimal.ONE);
                    } else {
                        unpaidLeaveDays = unpaidLeaveDays.add(BigDecimal.ONE);
                    }
                }
                case HOLIDAY -> holidayDays++;
                case WEEK_OFF -> weekOffDays++;
            }
        }

        BigDecimal attendancePercentage = totalWorkingDays > 0
                ? presentDays.multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalWorkingDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return AttendanceReportResponse.builder()
                .userId(employee.getId())
                .fullName(employee.getFullName())
                .empCode(employee.getEmpCode())
                .departmentName(employee.getDepartment() != null
                        ? employee.getDepartment().getName() : null)
                .designationName(employee.getDesignation() != null
                        ? employee.getDesignation().getName() : null)
                .month(month)
                .year(year)
                .totalWorkingDays(totalWorkingDays)
                .presentDays(scale1(presentDays))
                .absentDays(scale1(absentDays))
                .halfDays(scale1(halfDays))
                .paidLeaveDays(scale1(paidLeaveDays))
                .unpaidLeaveDays(scale1(unpaidLeaveDays))
                .onLeaveDays(scale1(onLeaveDays))
                .holidayDays(holidayDays)
                .weekOffDays(weekOffDays)
                .overtimeHours(scale2(overtimeHours))
                .attendancePercentage(attendancePercentage)
                .build();
    }

    // ============================================================
    //  Salary report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<SalaryReportResponse> getMonthlySalaryReport(
            Integer month, Integer year, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        List<SalaryPayment> payments = salaryPaymentRepository
                .findByUser_CompanyIdAndMonthAndYear(companyId, month, year);
        Map<Long, AdjustmentTotals> adjustmentsByUser = computeAdjustmentTotals(companyId, month, year);

        List<SalaryReportResponse> all = payments.stream()
                .map(payment -> buildSalaryReport(payment, adjustmentsByUser))
                .sorted(byEmpCode(SalaryReportResponse::getEmpCode))
                .toList();
        return sliceInMemory(all, pageable);
    }

    private SalaryReportResponse buildSalaryReport(SalaryPayment payment,
                                                   Map<Long, AdjustmentTotals> adjustmentsByUser) {
        User user = payment.getUser();
        SalaryStructure structure = payment.getSalaryStructure();
        AdjustmentTotals totals = adjustmentsByUser.getOrDefault(
                user != null ? user.getId() : null, AdjustmentTotals.zero());

        return SalaryReportResponse.builder()
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .departmentName(user != null && user.getDepartment() != null
                        ? user.getDepartment().getName() : null)
                .designationName(user != null && user.getDesignation() != null
                        ? user.getDesignation().getName() : null)
                .month(payment.getMonth())
                .year(payment.getYear())
                .salaryType(user != null && user.getSalaryType() != null
                        ? user.getSalaryType().name() : null)
                .basic(structure != null ? structure.getBasic() : null)
                .hra(structure != null ? structure.getHra() : null)
                .allowances(structure != null ? structure.getAllowances() : null)
                .grossSalary(payment.getGrossSalary())
                .pfDeduction(structure != null ? structure.getPfDeduction() : null)
                .otherDeductions(structure != null ? structure.getOtherDeductions() : null)
                .advanceDeduction(payment.getAdvanceDeduction())
                .totalAdjustmentAdditions(scale2(totals.additions()))
                .totalAdjustmentDeductions(scale2(totals.deductions()))
                .totalDeductions(payment.getTotalDeductions())
                .netSalary(payment.getNetSalary())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .paidOn(payment.getPaidOn())
                .build();
    }

    private Map<Long, AdjustmentTotals> computeAdjustmentTotals(Long companyId, Integer month, Integer year) {
        Map<Long, AdjustmentTotals> result = new HashMap<>();
        for (SalaryAdjustment adj : salaryAdjustmentRepository
                .findByCompanyIdAndMonthAndYear(companyId, month, year)) {
            Long userId = adj.getUser() != null ? adj.getUser().getId() : null;
            if (userId == null) {
                continue;
            }
            AdjustmentTotals totals = result.computeIfAbsent(userId, k -> new AdjustmentTotals());
            if (ADJ_TYPE_ADDITION.equalsIgnoreCase(adj.getType())) {
                totals.add(nullSafe(adj.getAmount()), BigDecimal.ZERO);
            } else if (ADJ_TYPE_DEDUCTION.equalsIgnoreCase(adj.getType())) {
                totals.add(BigDecimal.ZERO, nullSafe(adj.getAmount()));
            }
        }
        return result;
    }

    // ============================================================
    //  Leave report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<LeaveReportResponse> getLeaveReport(
            Integer month, Integer year, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<User> employees = userRepository.findByCompanyId(companyId);
        Map<Long, List<LeaveRequest>> leavesByUser = leaveRequestRepository
                .findByUser_CompanyIdAndFromDateBetween(companyId, monthStart, monthEnd).stream()
                .collect(Collectors.groupingBy(lr -> lr.getUser().getId()));

        List<LeaveReportResponse> all = employees.stream()
                .map(employee -> buildLeaveReport(
                        employee, month, year,
                        leavesByUser.getOrDefault(employee.getId(), List.of())))
                .sorted(byEmpCode(LeaveReportResponse::getEmpCode))
                .toList();
        return sliceInMemory(all, pageable);
    }

    private LeaveReportResponse buildLeaveReport(User employee, Integer month, Integer year,
                                                 List<LeaveRequest> leaves) {
        BigDecimal totalLeavesTaken = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalUnpaid = BigDecimal.ZERO;
        for (LeaveRequest lr : leaves) {
            BigDecimal days = nullSafe(lr.getTotalDays());
            totalLeavesTaken = totalLeavesTaken.add(days);
            boolean paid = lr.getLeaveType() != null
                    && Boolean.TRUE.equals(lr.getLeaveType().getIsPaid());
            if (paid) {
                totalPaid = totalPaid.add(days);
            } else {
                totalUnpaid = totalUnpaid.add(days);
            }
        }

        List<LeaveRequestResponse> leaveResponses = leaves.stream()
                .map(this::toLeaveRequestResponse)
                .toList();

        return LeaveReportResponse.builder()
                .userId(employee.getId())
                .fullName(employee.getFullName())
                .empCode(employee.getEmpCode())
                .departmentName(employee.getDepartment() != null
                        ? employee.getDepartment().getName() : null)
                .month(month)
                .year(year)
                .leaveRequests(leaveResponses)
                .totalLeavesTaken(scale1(totalLeavesTaken))
                .totalPaidLeaves(scale1(totalPaid))
                .totalUnpaidLeaves(scale1(totalUnpaid))
                .build();
    }

    // ============================================================
    //  Advance report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<AdvanceReportResponse> getAdvanceReport(Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Map<Long, List<Advance>> advancesByUser = advanceRepository
                .findByUser_CompanyIdOrderByCreatedAtDesc(companyId).stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));
        if (advancesByUser.isEmpty()) {
            return sliceInMemory(List.of(), pageable);
        }

        Map<Long, User> usersById = userRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AdvanceReportResponse> all = advancesByUser.entrySet().stream()
                .map(entry -> {
                    User employee = usersById.get(entry.getKey());
                    if (employee == null) {
                        // Defensive: advance row references a user from a different company.
                        // Shouldn't happen if data integrity is intact; log and skip.
                        log.warn("Skipping advance report group — user id={} not found in company={}",
                                entry.getKey(), companyId);
                        return null;
                    }
                    return buildAdvanceReport(employee, entry.getValue());
                })
                .filter(Objects::nonNull)
                .sorted(byEmpCode(AdvanceReportResponse::getEmpCode))
                .toList();
        return sliceInMemory(all, pageable);
    }

    private AdvanceReportResponse buildAdvanceReport(User employee, List<Advance> advances) {
        BigDecimal totalRequested = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal totalRecovered = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;
        for (Advance a : advances) {
            BigDecimal amount = nullSafe(a.getAmount());
            totalRequested = totalRequested.add(amount);
            if (a.getStatus() == AdvanceStatus.APPROVED) {
                totalApproved = totalApproved.add(amount);
            }
            if (Boolean.TRUE.equals(a.getIsRecovered())) {
                totalRecovered = totalRecovered.add(amount);
            }
            if (a.getStatus() == AdvanceStatus.PENDING) {
                totalPending = totalPending.add(amount);
            }
        }
        return AdvanceReportResponse.builder()
                .userId(employee.getId())
                .fullName(employee.getFullName())
                .empCode(employee.getEmpCode())
                .departmentName(employee.getDepartment() != null
                        ? employee.getDepartment().getName() : null)
                .totalAdvanceRequested(scale2(totalRequested))
                .totalAdvanceApproved(scale2(totalApproved))
                .totalAdvanceRecovered(scale2(totalRecovered))
                .totalAdvancePending(scale2(totalPending))
                .advances(advances.stream().map(this::toAdvanceResponse).toList())
                .build();
    }

    // ============================================================
    //  Helpers
    // ============================================================

    /**
     * Counts working days in a date range — excludes the company's weekly off
     * day (parsed from {@code settings.weekOffDay}) and any holidays in the
     * range. Mirrors {@link PayrollService#calculateWorkingDays} so attendance
     * reports stay in lock-step with payroll math.
     */
    private Integer calculateWorkingDays(LocalDate from, LocalDate to,
                                         CompanySettings settings, Long companyId) {
        DayOfWeek weekOff = parseDayOfWeek(settings.getWeekOffDay());
        Set<LocalDate> holidays = holidayRepository
                .findByCompanyIdAndHolidayDateBetween(companyId, from, to).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int working = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            boolean skip = (weekOff != null && cursor.getDayOfWeek() == weekOff)
                    || holidays.contains(cursor);
            if (!skip) {
                working++;
            }
            cursor = cursor.plusDays(1);
        }
        return working;
    }

    private Map<LocalDate, Boolean> buildLeavePaidMap(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findOverlappingApprovedLeaves(userId, LeaveRequestStatus.APPROVED, monthStart, monthEnd);
        Map<LocalDate, Boolean> result = new HashMap<>();
        for (LeaveRequest lr : approvedLeaves) {
            boolean paid = lr.getLeaveType() != null
                    && Boolean.TRUE.equals(lr.getLeaveType().getIsPaid());
            LocalDate cursor = lr.getFromDate().isBefore(monthStart) ? monthStart : lr.getFromDate();
            LocalDate end = lr.getToDate().isAfter(monthEnd) ? monthEnd : lr.getToDate();
            while (!cursor.isAfter(end)) {
                result.merge(cursor, paid, (existing, fresh) -> existing || fresh);
                cursor = cursor.plusDays(1);
            }
        }
        return result;
    }

    private DayOfWeek parseDayOfWeek(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid week_off_day '{}'", value);
            return null;
        }
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new UnauthorizedException("Caller is not bound to a company");
        }
        return companyId;
    }

    private static <T> Comparator<T> byEmpCode(Function<T, String> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal scale1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Pagination for fully-materialised report rows. Reports aggregate across
     * employees so the full list must be built before slicing; given typical
     * HR team sizes this stays cheap. If a tenant ever grows large enough to
     * notice, the underlying query layer is where pagination should move.
     */
    private static <T> PageResponse<T> sliceInMemory(List<T> all, Pageable pageable) {
        int total = all.size();
        int pageSize = Math.max(pageable.getPageSize(), 1);
        int from = (int) Math.min(pageable.getOffset(), total);
        int to = Math.min(from + pageSize, total);
        List<T> content = from >= to ? List.of() : all.subList(from, to);
        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    // ============================================================
    //  Embedded mappers (duplicated from LeaveService / AdvanceService to
    //  keep the reports layer decoupled from those service classes)
    // ============================================================

    private LeaveRequestResponse toLeaveRequestResponse(LeaveRequest lr) {
        User user = lr.getUser();
        User actioner = lr.getActionedBy();
        return LeaveRequestResponse.builder()
                .id(lr.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .leaveTypeId(lr.getLeaveType() != null ? lr.getLeaveType().getId() : null)
                .leaveTypeName(lr.getLeaveType() != null ? lr.getLeaveType().getName() : null)
                .fromDate(lr.getFromDate())
                .toDate(lr.getToDate())
                .totalDays(lr.getTotalDays())
                .isHalfDay(lr.getIsHalfDay())
                .halfDayType(lr.getHalfDayType())
                .reason(lr.getReason())
                .status(lr.getStatus() != null ? lr.getStatus().name() : null)
                .rejectReason(lr.getRejectReason())
                .actionedByName(actioner != null ? actioner.getFullName() : null)
                .appliedAt(lr.getAppliedAt())
                .actionedAt(lr.getActionedAt())
                .build();
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
                .status(advance.getStatus() != null ? advance.getStatus().name() : null)
                .deductFromMonth(advance.getDeductFromMonth())
                .deductFromYear(advance.getDeductFromYear())
                .isRecovered(advance.getIsRecovered())
                .approvedByName(approver != null ? approver.getFullName() : null)
                .createdAt(advance.getCreatedAt())
                .actionedAt(advance.getActionedAt())
                .build();
    }

    // ============================================================
    //  Local helper struct
    // ============================================================

    /** Mutable running totals used while grouping {@link SalaryAdjustment} by user. */
    private static final class AdjustmentTotals {
        private BigDecimal additions = BigDecimal.ZERO;
        private BigDecimal deductions = BigDecimal.ZERO;

        void add(BigDecimal addition, BigDecimal deduction) {
            additions = additions.add(addition);
            deductions = deductions.add(deduction);
        }

        BigDecimal additions() {
            return additions;
        }

        BigDecimal deductions() {
            return deductions;
        }

        static AdjustmentTotals zero() {
            return new AdjustmentTotals();
        }
    }
}
