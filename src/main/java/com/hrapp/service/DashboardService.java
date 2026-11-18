package com.hrapp.service;

import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.EmployeeDashboardMonthSummaryResponse;
import com.hrapp.dto.response.EmployeeDashboardResponse;
import com.hrapp.dto.response.EmployeeDashboardShiftResponse;
import com.hrapp.dto.response.EmployeeDashboardUserResponse;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.dto.response.LeaveBalanceResponse;
import com.hrapp.dto.response.OpsDashboardCompanyResponse;
import com.hrapp.dto.response.OpsDashboardHeadcountResponse;
import com.hrapp.dto.response.OpsDashboardPayrollStatusResponse;
import com.hrapp.dto.response.OpsDashboardResponse;
import com.hrapp.dto.response.OpsDashboardTodayAttendanceSummaryResponse;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.Company;
import com.hrapp.entity.Holiday;
import com.hrapp.entity.User;
import com.hrapp.enums.AdvanceStatus;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.LeaveRequestStatus;
import com.hrapp.enums.PaymentStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AdvanceRepository;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveRequestRepository;
import com.hrapp.repository.SalaryPaymentRepository;
import com.hrapp.repository.ThumbLogRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Aggregates home-screen data for employee and ops dashboards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final String STATUS_ACTIVE = "Active";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final LeaveService leaveService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AdvanceRepository advanceRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;
    private final ThumbLogRepository thumbLogRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse getEmployeeDashboard() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        int year = today.getYear();

        AttendanceResponse todayAttendance = attendanceService.findTodayAttendanceOptional().orElse(null);
        EmployeeDashboardShiftResponse shift = buildShift(companyId);
        EmployeeDashboardMonthSummaryResponse monthSummary = buildMonthSummary(userId, currentMonth);
        List<LeaveBalanceResponse> leaveBalances = leaveService.getMyLeaveBalances(year);
        long pendingLeavesCount = leaveRequestRepository.countByUserIdAndStatus(
                userId, LeaveRequestStatus.PENDING);
        long pendingAdvancesCount = advanceRepository.countByUserIdAndStatus(
                userId, AdvanceStatus.PENDING);
        HolidayResponse nextHoliday = holidayRepository
                .findFirstByCompanyIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(companyId, today)
                .map(this::toHolidayResponse)
                .orElse(null);

        return EmployeeDashboardResponse.builder()
                .user(EmployeeDashboardUserResponse.builder()
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .empCode(user.getEmpCode())
                        .companyId(companyId)
                        .build())
                .todayAttendance(todayAttendance)
                .shift(shift)
                .monthSummary(monthSummary)
                .leaveBalances(leaveBalances)
                .pendingLeavesCount(pendingLeavesCount)
                .pendingAdvancesCount(pendingAdvancesCount)
                .nextHoliday(nextHoliday)
                .build();
    }

    /**
     * HR/ADMIN company home-screen aggregate: approval badges, today's
     * attendance pulse, headcount, thumb backlog, and this-month payroll.
     */
    @Transactional(readOnly = true)
    public OpsDashboardResponse getOpsDashboard() {
        Long companyId = requireCallerCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        long totalActiveEmployees = userRepository.countByCompanyIdAndStatus_Name(
                companyId, STATUS_ACTIVE);
        long pendingLeavesCount = leaveRequestRepository.countByUser_CompanyIdAndStatus(
                companyId, LeaveRequestStatus.PENDING);
        long pendingAdvancesCount = advanceRepository.countByUser_CompanyIdAndStatus(
                companyId, AdvanceStatus.PENDING);
        long pendingThumbLogsCount = thumbLogRepository.countByCompanyIdAndIsProcessedFalse(companyId);

        OpsDashboardTodayAttendanceSummaryResponse todaySummary =
                buildTodayAttendanceSummary(companyId, today, totalActiveEmployees);
        OpsDashboardPayrollStatusResponse payrollStatus =
                buildThisMonthPayrollStatus(companyId, currentMonth);

        return OpsDashboardResponse.builder()
                .company(OpsDashboardCompanyResponse.builder()
                        .companyId(company.getId())
                        .name(company.getName())
                        .build())
                .headcount(OpsDashboardHeadcountResponse.builder()
                        .totalActiveEmployees(totalActiveEmployees)
                        .build())
                .pendingLeavesCount(pendingLeavesCount)
                .pendingAdvancesCount(pendingAdvancesCount)
                .pendingThumbLogsCount(pendingThumbLogsCount)
                .todayAttendanceSummary(todaySummary)
                .thisMonthPayrollStatus(payrollStatus)
                .build();
    }

    private OpsDashboardTodayAttendanceSummaryResponse buildTodayAttendanceSummary(
            Long companyId, LocalDate today, long totalActiveEmployees) {
        List<Attendance> rows = attendanceRepository.findByCompanyIdAndAttendanceDate(companyId, today);

        int present = 0;
        int absent = 0;
        int halfDay = 0;
        int onLeave = 0;
        int weekOff = 0;
        int holiday = 0;
        int checkedInNow = 0;
        int checkedInToday = 0;

        for (Attendance row : rows) {
            AttendanceStatus status = row.getStatus();
            if (status != null) {
                switch (status) {
                    case PRESENT -> present++;
                    case ABSENT -> absent++;
                    case HALF_DAY -> halfDay++;
                    case ON_LEAVE -> onLeave++;
                    case WEEK_OFF -> weekOff++;
                    case HOLIDAY -> holiday++;
                }
            }
            if (row.getCheckIn() != null) {
                checkedInToday++;
                if (row.getCheckOut() == null) {
                    checkedInNow++;
                }
            }
        }

        int notCheckedIn = (int) Math.max(0, totalActiveEmployees - checkedInToday);

        return OpsDashboardTodayAttendanceSummaryResponse.builder()
                .present(present)
                .absent(absent)
                .halfDay(halfDay)
                .onLeave(onLeave)
                .weekOff(weekOff)
                .holiday(holiday)
                .checkedInNow(checkedInNow)
                .notCheckedIn(notCheckedIn)
                .build();
    }

    private OpsDashboardPayrollStatusResponse buildThisMonthPayrollStatus(
            Long companyId, YearMonth month) {
        int monthValue = month.getMonthValue();
        int year = month.getYear();
        long generated = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYear(
                companyId, monthValue, year);
        long paid = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYearAndStatus(
                companyId, monthValue, year, PaymentStatus.PAID);
        long unpaid = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYearAndStatus(
                companyId, monthValue, year, PaymentStatus.DRAFT);

        return OpsDashboardPayrollStatusResponse.builder()
                .month(monthValue)
                .year(year)
                .generated(generated)
                .paid(paid)
                .unpaid(unpaid)
                .build();
    }

    private EmployeeDashboardShiftResponse buildShift(Long companyId) {
        return companySettingsRepository.findByCompanyId(companyId)
                .map(settings -> EmployeeDashboardShiftResponse.builder()
                        .shiftStartTime(settings.getShiftStartTime())
                        .shiftEndTime(settings.getShiftEndTime())
                        .build())
                .orElse(null);
    }

    private EmployeeDashboardMonthSummaryResponse buildMonthSummary(Long userId, YearMonth month) {
        List<Attendance> rows = attendanceRepository.findByUserIdAndAttendanceDateBetween(
                userId, month.atDay(1), month.atEndOfMonth());

        int present = 0;
        int absent = 0;
        int halfDay = 0;
        int onLeave = 0;
        int weekOff = 0;
        int holiday = 0;

        for (Attendance row : rows) {
            AttendanceStatus status = row.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case HALF_DAY -> halfDay++;
                case ON_LEAVE -> onLeave++;
                case WEEK_OFF -> weekOff++;
                case HOLIDAY -> holiday++;
            }
        }

        return EmployeeDashboardMonthSummaryResponse.builder()
                .month(month.getMonthValue())
                .year(month.getYear())
                .presentDays(present)
                .absentDays(absent)
                .halfDays(halfDay)
                .onLeaveDays(onLeave)
                .weekOffDays(weekOff)
                .holidayDays(holiday)
                .build();
    }

    private HolidayResponse toHolidayResponse(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .companyId(holiday.getCompany() != null ? holiday.getCompany().getId() : null)
                .createdAt(holiday.getCreatedAt())
                .build();
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
}
