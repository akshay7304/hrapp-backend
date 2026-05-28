package com.hrapp.service;

import com.hrapp.dto.request.MarkPaidRequest;
import com.hrapp.dto.request.RunPayrollRequest;
import com.hrapp.dto.request.SalaryAdjustmentRequest;
import com.hrapp.dto.request.SalaryStructureRequest;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryAdjustmentResponse;
import com.hrapp.dto.response.SalaryPaymentResponse;
import com.hrapp.dto.response.SalaryStructureResponse;
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
import com.hrapp.enums.PaymentStatus;
import com.hrapp.enums.SalaryType;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AdvanceRepository;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveRequestRepository;
import com.hrapp.repository.SalaryAdjustmentRepository;
import com.hrapp.repository.SalaryPaymentRepository;
import com.hrapp.repository.SalaryStructureRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Salary structure management, monthly adjustments, and the payroll engine
 * (run / payslip / mark-paid). Multi-tenancy is enforced at every entry
 * point — caller's company is read from {@link SecurityUtil} and every
 * user / payment / adjustment fetched must belong to that company.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private static final String ADJ_TYPE_ADDITION = "ADDITION";
    private static final String ADJ_TYPE_DEDUCTION = "DEDUCTION";
    /**
     * Statuses that are excluded from a payroll run. Everyone else
     * (Active, Probation, Inactive, Suspended, On Notice Period, Contract)
     * receives a payslip so the audit trail covers partial-month edge cases
     * like notice-period payouts or settlement of suspended employees.
     */
    private static final List<String> PAYROLL_EXCLUDED_STATUSES = List.of("Terminated", "Resigned");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final UserRepository userRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final SalaryAdjustmentRepository salaryAdjustmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AdvanceRepository advanceRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    // ============================================================
    //  Salary structure
    // ============================================================

    @Transactional
    public SalaryStructureResponse setSalaryStructure(SalaryStructureRequest request) {
        Long companyId = requireCallerCompanyId();
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, companyId);

        LocalDate maxFuture = LocalDate.now().plusYears(1);
        if (request.getEffectiveFrom().isAfter(maxFuture)) {
            throw new BadRequestException("Effective from cannot be more than 1 year in the future");
        }

        SalaryStructure structure = SalaryStructure.builder()
                .user(user)
                .basic(request.getBasic())
                .hra(request.getHra())
                .allowances(request.getAllowances())
                .pfDeduction(request.getPfDeduction())
                .otherDeductions(request.getOtherDeductions())
                .overtimeRate(request.getOvertimeRate())
                .effectiveFrom(request.getEffectiveFrom())
                .build();
        structure = salaryStructureRepository.save(structure);
        log.info("Salary structure id={} created for user={} effectiveFrom={}",
                structure.getId(), user.getId(), structure.getEffectiveFrom());
        return toStructureResponse(structure);
    }

    @Transactional(readOnly = true)
    public List<SalaryStructureResponse> getSalaryStructure(Long userId) {
        Long companyId = requireCallerCompanyId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, companyId);
        return salaryStructureRepository.findByUserIdOrderByEffectiveFromDesc(userId).stream()
                .map(this::toStructureResponse)
                .toList();
    }

    // ============================================================
    //  Adjustments
    // ============================================================

    @Transactional
    public SalaryAdjustmentResponse addAdjustment(SalaryAdjustmentRequest request) {
        Long companyId = requireCallerCompanyId();
        Long creatorId = requireCallerUserId();

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, companyId);

        String normalizedType = normalizeAdjustmentType(request.getType());
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BadRequestException("Adjustment amount must be greater than zero");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator user not found"));

        SalaryAdjustment adjustment = SalaryAdjustment.builder()
                .user(user)
                .company(user.getCompany())
                .month(request.getMonth())
                .year(request.getYear())
                .type(normalizedType)
                .amount(request.getAmount())
                .reason(request.getReason())
                .createdBy(creator)
                .build();
        adjustment = salaryAdjustmentRepository.save(adjustment);
        log.info("Salary adjustment id={} type={} amount={} for user={} {}/{}",
                adjustment.getId(), normalizedType, adjustment.getAmount(),
                user.getId(), request.getMonth(), request.getYear());
        return toAdjustmentResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdjustmentResponse> getAdjustments(Long userId, Integer month, Integer year) {
        Long companyId = requireCallerCompanyId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, companyId);
        return salaryAdjustmentRepository.findByUserIdAndMonthAndYear(userId, month, year).stream()
                .map(this::toAdjustmentResponse)
                .toList();
    }

    @Transactional
    public void deleteAdjustment(Long id) {
        Long companyId = requireCallerCompanyId();
        SalaryAdjustment adjustment = salaryAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found"));
        ensureSameCompany(adjustment.getUser(), companyId);

        salaryPaymentRepository
                .findByUserIdAndMonthAndYear(adjustment.getUser().getId(),
                        adjustment.getMonth(), adjustment.getYear())
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .ifPresent(p -> {
                    throw new BadRequestException("Cannot delete adjustment for paid payroll");
                });

        salaryAdjustmentRepository.delete(adjustment);
        log.info("Deleted adjustment id={}", id);
    }

    // ============================================================
    //  Payroll run
    // ============================================================

    @Transactional
    public List<SalaryPaymentResponse> runPayroll(RunPayrollRequest request) {
        Long companyId = requireCallerCompanyId();
        Long generatorId = requireCallerUserId();
        int month = request.getMonth();
        int year = request.getYear();

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        Integer workingDays = calculateWorkingDays(month, year, settings, companyId);
        LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();

        User generator = userRepository.findById(generatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Generating user not found"));

        List<User> payrollEmployees = userRepository
                .findByCompanyIdAndStatus_NameNotIn(companyId, PAYROLL_EXCLUDED_STATUSES);
        List<SalaryAdjustment> allAdjustments = salaryAdjustmentRepository
                .findByCompanyIdAndMonthAndYear(companyId, month, year);
        Map<Long, List<SalaryAdjustment>> adjustmentsByUser = allAdjustments.stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));

        int processed = 0;
        int skipped = 0;
        for (User employee : payrollEmployees) {
            Optional<SalaryStructure> structureOpt = salaryStructureRepository
                    .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            employee.getId(), monthEnd);
            if (structureOpt.isEmpty()) {
                log.warn("Skipping payroll for user={} — no salary structure on/before {}",
                        employee.getId(), monthEnd);
                skipped++;
                continue;
            }
            SalaryStructure structure = structureOpt.get();

            Optional<SalaryPayment> existingOpt = salaryPaymentRepository
                    .findByUserIdAndMonthAndYear(employee.getId(), month, year);
            if (existingOpt.isPresent() && existingOpt.get().getStatus() == PaymentStatus.PAID) {
                log.info("Skipping payroll for user={} — already PAID", employee.getId());
                skipped++;
                continue;
            }

            AttendanceSummary summary = getAttendanceSummary(employee.getId(), month, year);
            List<SalaryAdjustment> adjustments =
                    adjustmentsByUser.getOrDefault(employee.getId(), List.of());

            List<Advance> advances = advanceRepository
                    .findByUserIdAndDeductFromMonthAndDeductFromYearAndStatusAndIsRecovered(
                            employee.getId(), month, year, AdvanceStatus.APPROVED, false);
            BigDecimal newAdvanceDeduction = advances.stream()
                    .map(Advance::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal advanceDeduction = existingOpt
                    .map(p -> p.getAdvanceDeduction().add(newAdvanceDeduction))
                    .orElse(newAdvanceDeduction);

            SalaryPayment computed = calculateNetSalary(
                    employee, structure, summary, workingDays, adjustments, advanceDeduction);

            SalaryPayment toPersist = existingOpt
                    .map(existing -> applyComputed(existing, structure, summary, computed))
                    .orElseGet(() -> {
                        computed.setMonth(month);
                        computed.setYear(year);
                        computed.setStatus(PaymentStatus.DRAFT);
                        return computed;
                    });
            toPersist.setGeneratedBy(generator);
            salaryPaymentRepository.save(toPersist);

            if (!advances.isEmpty()) {
                advances.forEach(a -> a.setIsRecovered(true));
                advanceRepository.saveAll(advances);
            }
            processed++;
        }
        log.info("Payroll run {}/{} for company={} — processed={} skipped={}",
                month, year, companyId, processed, skipped);

        return getPayroll(month, year);
    }

    @Transactional(readOnly = true)
    public List<SalaryPaymentResponse> getPayroll(Integer month, Integer year) {
        Long companyId = requireCallerCompanyId();
        List<SalaryPayment> payments = salaryPaymentRepository
                .findByUser_CompanyIdAndMonthAndYear(companyId, month, year);
        Map<Long, List<SalaryAdjustmentResponse>> adjustmentsByUser =
                loadAdjustmentsByUser(companyId, month, year);

        return payments.stream()
                .map(p -> toPaymentResponse(p,
                        adjustmentsByUser.getOrDefault(p.getUser().getId(), List.of())))
                .toList();
    }

    /**
     * Paginated payroll listing. Backs {@code GET /payroll/{month}/{year}}.
     * The adjustments map is built company-wide for the month, then looked up
     * per user on the current page — keeps the response shape identical to
     * the legacy list endpoint while still serving one page at a time.
     */
    @Transactional(readOnly = true)
    public PageResponse<SalaryPaymentResponse> getPayroll(
            Integer month, Integer year, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Pageable effective = applyDefaultSort(pageable, Sort.by("user.fullName"));
        Page<SalaryPayment> payments = salaryPaymentRepository
                .findByUser_CompanyIdAndMonthAndYear(companyId, month, year, effective);
        if (payments.isEmpty()) {
            return PageResponse.from(payments.map(p -> toPaymentResponse(p, List.of())));
        }
        Map<Long, List<SalaryAdjustmentResponse>> adjustmentsByUser =
                loadAdjustmentsByUser(companyId, month, year);
        return PageResponse.from(payments.map(p ->
                toPaymentResponse(p,
                        adjustmentsByUser.getOrDefault(p.getUser().getId(), List.of()))));
    }

    private Map<Long, List<SalaryAdjustmentResponse>> loadAdjustmentsByUser(
            Long companyId, Integer month, Integer year) {
        return salaryAdjustmentRepository
                .findByCompanyIdAndMonthAndYear(companyId, month, year).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getUser().getId(),
                        Collectors.mapping(this::toAdjustmentResponse, Collectors.toList())));
    }

    private Pageable applyDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    @Transactional(readOnly = true)
    public SalaryPaymentResponse getEmployeePayslip(Long employeeId, Integer month, Integer year) {
        Long companyId = requireCallerCompanyId();
        User user = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, companyId);
        SalaryPayment payment = salaryPaymentRepository
                .findByUserIdAndMonthAndYear(employeeId, month, year)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
        List<SalaryAdjustmentResponse> adjustments = salaryAdjustmentRepository
                .findByUserIdAndMonthAndYear(employeeId, month, year).stream()
                .map(this::toAdjustmentResponse)
                .toList();
        return toPaymentResponse(payment, adjustments);
    }

    @Transactional
    public SalaryPaymentResponse markAsPaid(Long id, MarkPaidRequest request) {
        Long companyId = requireCallerCompanyId();
        SalaryPayment payment = salaryPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
        ensureSameCompany(payment.getUser(), companyId);

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Already marked as paid");
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidOn(request.getPaidOn());
        payment.setRemarks(request.getRemarks());
        payment = salaryPaymentRepository.save(payment);
        log.info("Payslip id={} marked PAID on {}", id, request.getPaidOn());

        List<SalaryAdjustmentResponse> adjustments = salaryAdjustmentRepository
                .findByUserIdAndMonthAndYear(payment.getUser().getId(),
                        payment.getMonth(), payment.getYear()).stream()
                .map(this::toAdjustmentResponse)
                .toList();
        return toPaymentResponse(payment, adjustments);
    }

    @Transactional(readOnly = true)
    public SalaryPaymentResponse getMyPayslip(Integer month, Integer year) {
        Long userId = requireCallerUserId();
        SalaryPayment payment = salaryPaymentRepository
                .findByUserIdAndMonthAndYear(userId, month, year)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
        List<SalaryAdjustmentResponse> adjustments = salaryAdjustmentRepository
                .findByUserIdAndMonthAndYear(userId, month, year).stream()
                .map(this::toAdjustmentResponse)
                .toList();
        return toPaymentResponse(payment, adjustments);
    }

    // ============================================================
    //  Helpers
    // ============================================================

    Integer calculateWorkingDays(int month, int year, CompanySettings settings, Long companyId) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        DayOfWeek weekOff = parseDayOfWeek(settings.getWeekOffDay());
        Set<LocalDate> holidays = holidayRepository
                .findByCompanyIdAndHolidayDateBetween(companyId, monthStart, monthEnd).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int workingDays = 0;
        LocalDate cursor = monthStart;
        while (!cursor.isAfter(monthEnd)) {
            boolean skip = (weekOff != null && cursor.getDayOfWeek() == weekOff)
                    || holidays.contains(cursor);
            if (!skip) {
                workingDays++;
            }
            cursor = cursor.plusDays(1);
        }
        return workingDays;
    }

    AttendanceSummary getAttendanceSummary(Long userId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Attendance> attendance = attendanceRepository
                .findByUserIdAndAttendanceDateBetween(userId, monthStart, monthEnd);
        Map<LocalDate, Boolean> leavePaidByDate = buildLeavePaidMap(userId, monthStart, monthEnd);

        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal halfDays = BigDecimal.ZERO;
        BigDecimal paidLeaveDays = BigDecimal.ZERO;
        BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
        BigDecimal overtimeHours = BigDecimal.ZERO;
        BigDecimal totalWorkedHours = BigDecimal.ZERO;

        for (Attendance a : attendance) {
            totalWorkedHours = totalWorkedHours.add(nullSafe(a.getWorkedHours()));
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
                    Boolean paid = leavePaidByDate.get(a.getAttendanceDate());
                    if (Boolean.TRUE.equals(paid)) {
                        paidLeaveDays = paidLeaveDays.add(BigDecimal.ONE);
                    } else {
                        unpaidLeaveDays = unpaidLeaveDays.add(BigDecimal.ONE);
                    }
                }
                case HOLIDAY, WEEK_OFF -> {
                    // intentionally not counted
                }
            }
        }
        return AttendanceSummary.builder()
                .presentDays(scale1(presentDays))
                .absentDays(scale1(absentDays))
                .halfDays(scale1(halfDays))
                .paidLeaveDays(scale1(paidLeaveDays))
                .unpaidLeaveDays(scale1(unpaidLeaveDays))
                .overtimeHours(scale2(overtimeHours))
                .totalWorkedHours(scale2(totalWorkedHours))
                .build();
    }

    /**
     * Builds a per-date map of "is this date a paid leave day?" by scanning
     * every approved leave overlapping the supplied month window. When a single
     * date is covered by multiple leaves with conflicting paid flags, the paid
     * flag wins (employee-favourable) — should be a rare data anomaly.
     */
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

    SalaryPayment calculateNetSalary(User user, SalaryStructure structure,
                                     AttendanceSummary summary, Integer workingDays,
                                     List<SalaryAdjustment> adjustments,
                                     BigDecimal advanceDeduction) {
        SalaryType salaryType = user.getSalaryType() != null ? user.getSalaryType() : SalaryType.MONTHLY;
        BigDecimal basic = nullSafe(structure.getBasic());
        BigDecimal hra = nullSafe(structure.getHra());
        BigDecimal allowances = nullSafe(structure.getAllowances());
        BigDecimal pf = nullSafe(structure.getPfDeduction());
        BigDecimal other = nullSafe(structure.getOtherDeductions());
        BigDecimal otRate = nullSafe(structure.getOvertimeRate());

        BigDecimal earnedDays = summary.getPresentDays()
                .add(summary.getPaidLeaveDays())
                .add(summary.getHalfDays().multiply(HALF));

        BigDecimal earnedSalary = switch (salaryType) {
            case MONTHLY -> {
                BigDecimal monthlyTotal = basic.add(hra).add(allowances);
                BigDecimal perDayRate = workingDays > 0
                        ? monthlyTotal.divide(BigDecimal.valueOf(workingDays), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                yield perDayRate.multiply(earnedDays);
            }
            case DAILY -> basic.multiply(earnedDays);
            // For HOURLY employees the structure's `basic` is treated as the
            // hourly rate; gross pay is rate × hours actually worked.
            case HOURLY -> basic.multiply(summary.getTotalWorkedHours());
        };
        earnedSalary = scale2(earnedSalary);

        BigDecimal overtimePay = scale2(summary.getOvertimeHours().multiply(otRate));

        BigDecimal totalAdditions = BigDecimal.ZERO;
        BigDecimal totalDeductionAdjustments = BigDecimal.ZERO;
        for (SalaryAdjustment adj : adjustments) {
            if (ADJ_TYPE_ADDITION.equalsIgnoreCase(adj.getType())) {
                totalAdditions = totalAdditions.add(nullSafe(adj.getAmount()));
            } else if (ADJ_TYPE_DEDUCTION.equalsIgnoreCase(adj.getType())) {
                totalDeductionAdjustments = totalDeductionAdjustments.add(nullSafe(adj.getAmount()));
            }
        }
        totalAdditions = scale2(totalAdditions);
        totalDeductionAdjustments = scale2(totalDeductionAdjustments);

        BigDecimal grossSalary = scale2(earnedSalary.add(overtimePay).add(totalAdditions));
        BigDecimal totalDeductions = scale2(pf.add(other)
                .add(nullSafe(advanceDeduction))
                .add(totalDeductionAdjustments));
        BigDecimal netSalary = scale2(grossSalary.subtract(totalDeductions));

        return SalaryPayment.builder()
                .user(user)
                .salaryStructure(structure)
                .workingDays(workingDays)
                .presentDays(summary.getPresentDays())
                .absentDays(summary.getAbsentDays())
                .halfDays(summary.getHalfDays())
                .paidLeaveDays(summary.getPaidLeaveDays())
                .unpaidLeaveDays(summary.getUnpaidLeaveDays())
                .overtimeHours(summary.getOvertimeHours())
                .overtimePay(overtimePay)
                .grossSalary(grossSalary)
                .advanceDeduction(scale2(nullSafe(advanceDeduction)))
                .totalDeductions(totalDeductions)
                .netSalary(netSalary)
                .build();
    }

    /**
     * Copies the recomputed fields from {@code computed} onto {@code existing}
     * (DRAFT update path). Identity / month / year / status / paidOn are left
     * untouched; the caller fills in {@code generatedBy} afterwards.
     */
    private SalaryPayment applyComputed(SalaryPayment existing, SalaryStructure structure,
                                        AttendanceSummary summary, SalaryPayment computed) {
        existing.setSalaryStructure(structure);
        existing.setWorkingDays(computed.getWorkingDays());
        existing.setPresentDays(summary.getPresentDays());
        existing.setAbsentDays(summary.getAbsentDays());
        existing.setHalfDays(summary.getHalfDays());
        existing.setPaidLeaveDays(summary.getPaidLeaveDays());
        existing.setUnpaidLeaveDays(summary.getUnpaidLeaveDays());
        existing.setOvertimeHours(summary.getOvertimeHours());
        existing.setOvertimePay(computed.getOvertimePay());
        existing.setGrossSalary(computed.getGrossSalary());
        existing.setAdvanceDeduction(computed.getAdvanceDeduction());
        existing.setTotalDeductions(computed.getTotalDeductions());
        existing.setNetSalary(computed.getNetSalary());
        return existing;
    }

    private String normalizeAdjustmentType(String raw) {
        if (raw == null) {
            throw new BadRequestException("Adjustment type is required");
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!ADJ_TYPE_ADDITION.equals(upper) && !ADJ_TYPE_DEDUCTION.equals(upper)) {
            throw new BadRequestException("Adjustment type must be ADDITION or DEDUCTION");
        }
        return upper;
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
            log.warn("Cross-company payroll access blocked — caller={} target={}",
                    callerCompanyId, userCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
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

    // ============================================================
    //  Mappers
    // ============================================================

    private SalaryStructureResponse toStructureResponse(SalaryStructure structure) {
        BigDecimal gross = scale2(nullSafe(structure.getBasic())
                .add(nullSafe(structure.getHra()))
                .add(nullSafe(structure.getAllowances())));
        User user = structure.getUser();
        return SalaryStructureResponse.builder()
                .id(structure.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .basic(structure.getBasic())
                .hra(structure.getHra())
                .allowances(structure.getAllowances())
                .pfDeduction(structure.getPfDeduction())
                .otherDeductions(structure.getOtherDeductions())
                .overtimeRate(structure.getOvertimeRate())
                .effectiveFrom(structure.getEffectiveFrom())
                .grossSalary(gross)
                .createdAt(structure.getCreatedAt())
                .build();
    }

    private SalaryAdjustmentResponse toAdjustmentResponse(SalaryAdjustment adjustment) {
        User user = adjustment.getUser();
        User creator = adjustment.getCreatedBy();
        return SalaryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .month(adjustment.getMonth())
                .year(adjustment.getYear())
                .type(adjustment.getType())
                .amount(adjustment.getAmount())
                .reason(adjustment.getReason())
                .createdByName(creator != null ? creator.getFullName() : null)
                .createdAt(adjustment.getCreatedAt())
                .build();
    }

    private SalaryPaymentResponse toPaymentResponse(SalaryPayment payment,
                                                   List<SalaryAdjustmentResponse> adjustments) {
        User user = payment.getUser();
        SalaryStructure structure = payment.getSalaryStructure();
        User generator = payment.getGeneratedBy();

        BigDecimal totalAdditions = adjustments.stream()
                .filter(a -> ADJ_TYPE_ADDITION.equalsIgnoreCase(a.getType()))
                .map(SalaryAdjustmentResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductionAdjustments = adjustments.stream()
                .filter(a -> ADJ_TYPE_DEDUCTION.equalsIgnoreCase(a.getType()))
                .map(SalaryAdjustmentResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalaryPaymentResponse.builder()
                .id(payment.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .month(payment.getMonth())
                .year(payment.getYear())
                .salaryType(user != null && user.getSalaryType() != null
                        ? user.getSalaryType().name() : null)
                .workingDays(payment.getWorkingDays())
                .presentDays(payment.getPresentDays())
                .absentDays(payment.getAbsentDays())
                .halfDays(payment.getHalfDays())
                .paidLeaveDays(payment.getPaidLeaveDays())
                .unpaidLeaveDays(payment.getUnpaidLeaveDays())
                .overtimeHours(payment.getOvertimeHours())
                .overtimePay(payment.getOvertimePay())
                .basic(structure != null ? structure.getBasic() : null)
                .hra(structure != null ? structure.getHra() : null)
                .allowances(structure != null ? structure.getAllowances() : null)
                .grossSalary(payment.getGrossSalary())
                .pfDeduction(structure != null ? structure.getPfDeduction() : null)
                .otherDeductions(structure != null ? structure.getOtherDeductions() : null)
                .advanceDeduction(payment.getAdvanceDeduction())
                .totalAdjustmentAdditions(scale2(totalAdditions))
                .totalAdjustmentDeductions(scale2(totalDeductionAdjustments))
                .totalDeductions(payment.getTotalDeductions())
                .netSalary(payment.getNetSalary())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .paidOn(payment.getPaidOn())
                .remarks(payment.getRemarks())
                .adjustments(adjustments)
                .generatedByName(generator != null ? generator.getFullName() : null)
                .createdAt(payment.getCreatedAt())
                .build();
    }

    // ============================================================
    //  Inner DTO — attendance summary
    // ============================================================

    /**
     * Computed view of a user's attendance for a single month. All
     * day counters are {@code BigDecimal} so half-days fit cleanly and
     * hourly fields preserve 2-decimal precision.
     */
    @Getter
    @Builder
    public static class AttendanceSummary {
        private final BigDecimal presentDays;
        private final BigDecimal absentDays;
        private final BigDecimal halfDays;
        private final BigDecimal paidLeaveDays;
        private final BigDecimal unpaidLeaveDays;
        private final BigDecimal overtimeHours;
        private final BigDecimal totalWorkedHours;
    }
}
