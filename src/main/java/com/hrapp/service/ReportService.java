package com.hrapp.service;

import com.hrapp.dto.response.AdvanceReportResponse;
import com.hrapp.dto.response.AdvanceResponse;
import com.hrapp.dto.response.AttendanceReportResponse;
import com.hrapp.dto.response.LeaveReportResponse;
import com.hrapp.dto.response.LeaveRequestResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryReportResponse;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.Holiday;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AdjustmentTotalsRow;
import com.hrapp.repository.AdvanceReportUserRow;
import com.hrapp.repository.AdvanceRow;
import com.hrapp.repository.AttendanceReportDayRow;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveReportLeaveRow;
import com.hrapp.repository.ReportEmployeeRow;
import com.hrapp.repository.ReportJdbcRepository;
import com.hrapp.repository.SalaryReportRow;
import com.hrapp.security.AccountAccessService;
import com.hrapp.security.SecurityUtil;
import com.hrapp.util.WeekOffSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON reporting endpoints for HR/Admin dashboards. Every report is scoped to
 * the caller's company via {@link SecurityUtil}. List endpoints page in Postgres
 * via {@link ReportJdbcRepository}; attendance day math still runs in-memory
 * for the current page only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ATTENDANCE_EXCLUDED_STATUSES =
            List.copyOf(AccountAccessService.WORKFORCE_EXCLUDED_STATUSES);

    private final ReportJdbcRepository reportJdbcRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsRepository companySettingsRepository;

    // ============================================================
    //  Attendance report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<AttendanceReportResponse> getMonthlyAttendanceReport(
            Integer month, Integer year, int page, int size) {
        validateReportListPage(page, size);
        requireMonthYear(month, year);
        Long companyId = requireCallerCompanyId();
        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        Integer totalWorkingDays = calculateWorkingDays(monthStart, monthEnd, settings, companyId);
        Set<LocalDate> holidays = holidayRepository
                .findByCompanyIdAndHolidayDateBetween(companyId, monthStart, monthEnd).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        long offset = (long) page * size;
        List<ReportEmployeeRow> employees = reportJdbcRepository.getAttendanceReportEmployees(
                companyId, ATTENDANCE_EXCLUDED_STATUSES, size, offset);
        long total = reportJdbcRepository.getAttendanceReportEmployeesCount(
                companyId, ATTENDANCE_EXCLUDED_STATUSES);
        if (employees.isEmpty()) {
            return PageResponse.of(List.of(), page, size, total);
        }

        List<Long> userIds = employees.stream().map(ReportEmployeeRow::userId).toList();
        Map<Long, Map<LocalDate, AttendanceReportDayRow>> attendanceByUser = reportJdbcRepository
                .getAttendanceDaysByUsersAndDateRange(userIds, monthStart, monthEnd).stream()
                .collect(Collectors.groupingBy(
                        AttendanceReportDayRow::userId,
                        Collectors.toMap(
                                AttendanceReportDayRow::attendanceDate,
                                row -> row,
                                (a, b) -> a)));
        Map<Long, Map<LocalDate, Boolean>> paidLeaveByUser = buildPaidLeaveMapsByUser(
                reportJdbcRepository.getApprovedOverlappingLeavesByUsers(userIds, monthStart, monthEnd),
                monthStart,
                monthEnd);

        List<AttendanceReportResponse> content = employees.stream()
                .map(employee -> buildAttendanceReport(
                        employee,
                        month,
                        year,
                        monthStart,
                        monthEnd,
                        totalWorkingDays,
                        settings,
                        holidays,
                        attendanceByUser.getOrDefault(employee.userId(), Map.of()),
                        paidLeaveByUser.getOrDefault(employee.userId(), Map.of())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    private AttendanceReportResponse buildAttendanceReport(
            ReportEmployeeRow employee,
            Integer month,
            Integer year,
            LocalDate monthStart,
            LocalDate monthEnd,
            Integer totalWorkingDays,
            CompanySettings settings,
            Set<LocalDate> holidays,
            Map<LocalDate, AttendanceReportDayRow> byDate,
            Map<LocalDate, Boolean> paidLeaveMap) {

        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal halfDays = BigDecimal.ZERO;
        BigDecimal onLeaveDays = BigDecimal.ZERO;
        BigDecimal paidLeaveDays = BigDecimal.ZERO;
        BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
        int holidayDays = 0;
        int weekOffDays = 0;
        BigDecimal overtimeHours = BigDecimal.ZERO;

        LocalDate cursor = monthStart;
        while (!cursor.isAfter(monthEnd)) {
            boolean weekOff = WeekOffSupport.isWeekOff(cursor, settings.getWeekOffDay());
            boolean holiday = holidays.contains(cursor);
            if (weekOff) {
                weekOffDays++;
                cursor = cursor.plusDays(1);
                continue;
            }
            if (holiday) {
                holidayDays++;
                cursor = cursor.plusDays(1);
                continue;
            }

            AttendanceReportDayRow a = byDate.get(cursor);
            if (a == null) {
                absentDays = absentDays.add(BigDecimal.ONE);
                cursor = cursor.plusDays(1);
                continue;
            }

            overtimeHours = overtimeHours.add(nullSafe(a.overtimeHours()));
            AttendanceStatus status = parseAttendanceStatus(a.status());
            if (status == null) {
                absentDays = absentDays.add(BigDecimal.ONE);
            } else {
                switch (status) {
                    case PRESENT -> {
                        if (a.checkOut() != null) {
                            presentDays = presentDays.add(BigDecimal.ONE);
                        } else {
                            absentDays = absentDays.add(BigDecimal.ONE);
                        }
                    }
                    case ABSENT -> absentDays = absentDays.add(BigDecimal.ONE);
                    case HALF_DAY -> halfDays = halfDays.add(BigDecimal.ONE);
                    case ON_LEAVE -> {
                        onLeaveDays = onLeaveDays.add(BigDecimal.ONE);
                        if (Boolean.TRUE.equals(paidLeaveMap.get(a.attendanceDate()))) {
                            paidLeaveDays = paidLeaveDays.add(BigDecimal.ONE);
                        } else {
                            unpaidLeaveDays = unpaidLeaveDays.add(BigDecimal.ONE);
                        }
                    }
                    case HOLIDAY -> holidayDays++;
                    case WEEK_OFF -> weekOffDays++;
                }
            }
            cursor = cursor.plusDays(1);
        }

        BigDecimal earnedDays = presentDays
                .add(halfDays.multiply(new BigDecimal("0.5")))
                .add(paidLeaveDays);
        BigDecimal attendancePercentage = totalWorkingDays > 0
                ? earnedDays.multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalWorkingDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return AttendanceReportResponse.builder()
                .userId(employee.userId())
                .fullName(employee.fullName())
                .empCode(employee.empCode())
                .departmentName(employee.departmentName())
                .designationName(employee.designationName())
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
            Integer month, Integer year, int page, int size) {
        validateReportListPage(page, size);
        requireMonthYear(month, year);
        Long companyId = requireCallerCompanyId();
        long offset = (long) page * size;
        List<SalaryReportRow> rows = reportJdbcRepository
                .getSalaryReportByCompanyMonthYear(companyId, month, year, size, offset);
        long total = reportJdbcRepository.getSalaryReportByCompanyMonthYearCount(companyId, month, year);
        Map<Long, AdjustmentTotalsRow> adjustmentsByUser = reportJdbcRepository
                .getAdjustmentTotalsByCompanyMonthYear(companyId, month, year).stream()
                .collect(Collectors.toMap(AdjustmentTotalsRow::userId, row -> row, (a, b) -> a));

        List<SalaryReportResponse> content = rows.stream()
                .map(row -> buildSalaryReport(row, adjustmentsByUser))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    private SalaryReportResponse buildSalaryReport(
            SalaryReportRow row, Map<Long, AdjustmentTotalsRow> adjustmentsByUser) {
        AdjustmentTotalsRow totals = adjustmentsByUser.get(row.userId());
        BigDecimal additions = totals != null ? nullSafe(totals.additions()) : BigDecimal.ZERO;
        BigDecimal deductions = totals != null ? nullSafe(totals.deductions()) : BigDecimal.ZERO;
        return SalaryReportResponse.builder()
                .userId(row.userId())
                .fullName(row.fullName())
                .empCode(row.empCode())
                .departmentName(row.departmentName())
                .designationName(row.designationName())
                .month(row.month())
                .year(row.year())
                .salaryType(row.salaryType())
                .basic(row.basic())
                .hra(row.hra())
                .allowances(row.allowances())
                .grossSalary(row.grossSalary())
                .pfDeduction(row.pfDeduction())
                .otherDeductions(row.otherDeductions())
                .advanceDeduction(row.advanceDeduction())
                .totalAdjustmentAdditions(scale2(additions))
                .totalAdjustmentDeductions(scale2(deductions))
                .totalDeductions(row.totalDeductions())
                .netSalary(row.netSalary())
                .status(row.status())
                .paidOn(row.paidOn())
                .build();
    }

    // ============================================================
    //  Leave report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<LeaveReportResponse> getLeaveReport(
            Integer month, Integer year, int page, int size) {
        validateReportListPage(page, size);
        requireMonthYear(month, year);
        Long companyId = requireCallerCompanyId();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        long offset = (long) page * size;
        List<ReportEmployeeRow> employees =
                reportJdbcRepository.getLeaveReportEmployees(companyId, size, offset);
        long total = reportJdbcRepository.getLeaveReportEmployeesCount(companyId);
        if (employees.isEmpty()) {
            return PageResponse.of(List.of(), page, size, total);
        }

        List<Long> userIds = employees.stream().map(ReportEmployeeRow::userId).toList();
        Map<Long, List<LeaveReportLeaveRow>> leavesByUser = reportJdbcRepository
                .getOverlappingLeavesByUsers(userIds, monthStart, monthEnd).stream()
                .collect(Collectors.groupingBy(LeaveReportLeaveRow::userId));

        List<LeaveReportResponse> content = employees.stream()
                .map(employee -> buildLeaveReport(
                        employee, month, year, leavesByUser.getOrDefault(employee.userId(), List.of())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    private LeaveReportResponse buildLeaveReport(
            ReportEmployeeRow employee,
            Integer month,
            Integer year,
            List<LeaveReportLeaveRow> leaves) {
        BigDecimal totalLeavesTaken = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalUnpaid = BigDecimal.ZERO;
        for (LeaveReportLeaveRow lr : leaves) {
            BigDecimal days = nullSafe(lr.totalDays());
            totalLeavesTaken = totalLeavesTaken.add(days);
            if (Boolean.TRUE.equals(lr.leaveTypeIsPaid())) {
                totalPaid = totalPaid.add(days);
            } else {
                totalUnpaid = totalUnpaid.add(days);
            }
        }

        return LeaveReportResponse.builder()
                .userId(employee.userId())
                .fullName(employee.fullName())
                .empCode(employee.empCode())
                .departmentName(employee.departmentName())
                .month(month)
                .year(year)
                .leaveRequests(leaves.stream().map(this::toLeaveRequestResponse).toList())
                .totalLeavesTaken(scale1(totalLeavesTaken))
                .totalPaidLeaves(scale1(totalPaid))
                .totalUnpaidLeaves(scale1(totalUnpaid))
                .build();
    }

    // ============================================================
    //  Advance report
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<AdvanceReportResponse> getAdvanceReport(int page, int size) {
        validateReportListPage(page, size);
        Long companyId = requireCallerCompanyId();
        long offset = (long) page * size;
        List<AdvanceReportUserRow> users =
                reportJdbcRepository.getAdvanceReportUsers(companyId, size, offset);
        long total = reportJdbcRepository.getAdvanceReportUsersCount(companyId);
        if (users.isEmpty()) {
            return PageResponse.of(List.of(), page, size, total);
        }

        List<Long> userIds = users.stream().map(AdvanceReportUserRow::userId).toList();
        Map<Long, List<AdvanceRow>> advancesByUser = reportJdbcRepository.getAdvancesByUserIds(userIds)
                .stream()
                .collect(Collectors.groupingBy(AdvanceRow::userId));

        List<AdvanceReportResponse> content = users.stream()
                .map(user -> AdvanceReportResponse.builder()
                        .userId(user.userId())
                        .fullName(user.fullName())
                        .empCode(user.empCode())
                        .departmentName(user.departmentName())
                        .totalAdvanceRequested(scale2(nullSafe(user.totalAdvanceRequested())))
                        .totalAdvanceApproved(scale2(nullSafe(user.totalAdvanceApproved())))
                        .totalAdvanceRecovered(scale2(nullSafe(user.totalAdvanceRecovered())))
                        .totalAdvancePending(scale2(nullSafe(user.totalAdvancePending())))
                        .advances(advancesByUser.getOrDefault(user.userId(), List.of()).stream()
                                .map(this::toAdvanceResponse)
                                .toList())
                        .build())
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private Integer calculateWorkingDays(
            LocalDate from, LocalDate to, CompanySettings settings, Long companyId) {
        Set<LocalDate> holidays = holidayRepository
                .findByCompanyIdAndHolidayDateBetween(companyId, from, to).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int working = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            boolean skip = WeekOffSupport.isWeekOff(cursor, settings.getWeekOffDay())
                    || holidays.contains(cursor);
            if (!skip) {
                working++;
            }
            cursor = cursor.plusDays(1);
        }
        return working;
    }

    private static Map<Long, Map<LocalDate, Boolean>> buildPaidLeaveMapsByUser(
            List<LeaveReportLeaveRow> approvedLeaves, LocalDate monthStart, LocalDate monthEnd) {
        Map<Long, Map<LocalDate, Boolean>> result = new HashMap<>();
        for (LeaveReportLeaveRow lr : approvedLeaves) {
            boolean paid = Boolean.TRUE.equals(lr.leaveTypeIsPaid());
            LocalDate cursor = lr.fromDate().isBefore(monthStart) ? monthStart : lr.fromDate();
            LocalDate end = lr.toDate().isAfter(monthEnd) ? monthEnd : lr.toDate();
            Map<LocalDate, Boolean> byDate =
                    result.computeIfAbsent(lr.userId(), ignored -> new HashMap<>());
            while (!cursor.isAfter(end)) {
                byDate.merge(cursor, paid, (existing, fresh) -> existing || fresh);
                cursor = cursor.plusDays(1);
            }
        }
        return result;
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new UnauthorizedException("Caller is not bound to a company");
        }
        return companyId;
    }

    private static void requireMonthYear(Integer month, Integer year) {
        if (month == null || year == null) {
            throw new BadRequestException("month and year are required");
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("Month must be between 1 and 12");
        }
    }

    private static void validateReportListPage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be less than zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static AttendanceStatus parseAttendanceStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AttendanceStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
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

    private LeaveRequestResponse toLeaveRequestResponse(LeaveReportLeaveRow lr) {
        return LeaveRequestResponse.builder()
                .id(lr.id())
                .userId(lr.userId())
                .fullName(lr.fullName())
                .empCode(lr.empCode())
                .leaveTypeId(lr.leaveTypeId())
                .leaveTypeName(lr.leaveTypeName())
                .fromDate(lr.fromDate())
                .toDate(lr.toDate())
                .totalDays(lr.totalDays())
                .isHalfDay(lr.isHalfDay())
                .halfDayType(lr.halfDayType())
                .reason(lr.reason())
                .status(lr.status())
                .rejectReason(lr.rejectReason())
                .actionedByName(lr.actionedByName())
                .appliedAt(lr.appliedAt())
                .actionedAt(lr.actionedAt())
                .build();
    }

    private AdvanceResponse toAdvanceResponse(AdvanceRow advance) {
        return AdvanceResponse.builder()
                .id(advance.id())
                .userId(advance.userId())
                .fullName(advance.fullName())
                .empCode(advance.empCode())
                .amount(advance.amount())
                .reason(advance.reason())
                .status(advance.status())
                .deductFromMonth(advance.deductFromMonth())
                .deductFromYear(advance.deductFromYear())
                .isRecovered(advance.isRecovered())
                .approvedByName(advance.approvedByName())
                .createdAt(advance.createdAt())
                .actionedAt(advance.actionedAt())
                .build();
    }
}
