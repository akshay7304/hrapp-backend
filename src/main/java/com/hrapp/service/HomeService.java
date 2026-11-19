package com.hrapp.service;

import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.dto.response.HomeMeMonthSummaryResponse;
import com.hrapp.dto.response.HomeMeResponse;
import com.hrapp.dto.response.HomeMeShiftResponse;
import com.hrapp.dto.response.HomeMeUserResponse;
import com.hrapp.dto.response.HomeOpsCompanyResponse;
import com.hrapp.dto.response.HomeOpsHeadcountResponse;
import com.hrapp.dto.response.HomeOpsPayrollStatusResponse;
import com.hrapp.dto.response.HomeOpsResponse;
import com.hrapp.dto.response.HomeOpsTodayAttendanceSummaryResponse;
import com.hrapp.dto.response.HomeResponse;
import com.hrapp.dto.response.LeaveBalanceResponse;
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
 * Single home-screen aggregate. Role comes from the JWT; EMPLOYEE gets {@code me}
 * only, ADMIN/HR also get {@code ops}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeService {

    private static final String STATUS_ACTIVE = "Active";
    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_HR = "HR";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";

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
    public HomeResponse getHome() {
        String role = resolvePrimaryRole();
        Long companyId = SecurityUtil.getCurrentUserCompanyId();

        if (ROLE_SUPERADMIN.equals(role) && companyId == null) {
            return HomeResponse.builder()
                    .role(role)
                    .me(null)
                    .ops(null)
                    .build();
        }

        Long effectiveCompanyId = requireCallerCompanyId();
        HomeMeResponse me = buildMe(effectiveCompanyId);
        HomeOpsResponse ops = null;
        if (SecurityUtil.hasRole(ROLE_ADMIN) || SecurityUtil.hasRole(ROLE_HR)
                || SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            ops = buildOps(effectiveCompanyId);
        }

        return HomeResponse.builder()
                .role(role)
                .me(me)
                .ops(ops)
                .build();
    }

    private HomeMeResponse buildMe(Long companyId) {
        Long userId = requireCallerUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        int year = today.getYear();

        AttendanceResponse todayAttendance = attendanceService.findTodayAttendanceOptional().orElse(null);
        HomeMeShiftResponse shift = buildShift(companyId);
        HomeMeMonthSummaryResponse monthSummary = buildMonthSummary(userId, currentMonth);
        List<LeaveBalanceResponse> leaveBalances = leaveService.getMyLeaveBalances(year);
        long pendingLeavesCount = leaveRequestRepository.countByUserIdAndStatus(
                userId, LeaveRequestStatus.PENDING);
        long pendingAdvancesCount = advanceRepository.countByUserIdAndStatus(
                userId, AdvanceStatus.PENDING);
        HolidayResponse nextHoliday = holidayRepository
                .findFirstByCompanyIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(companyId, today)
                .map(this::toHolidayResponse)
                .orElse(null);

        return HomeMeResponse.builder()
                .user(HomeMeUserResponse.builder()
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

    private HomeOpsResponse buildOps(Long companyId) {
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

        return HomeOpsResponse.builder()
                .company(HomeOpsCompanyResponse.builder()
                        .companyId(company.getId())
                        .name(company.getName())
                        .build())
                .headcount(HomeOpsHeadcountResponse.builder()
                        .totalActiveEmployees(totalActiveEmployees)
                        .build())
                .pendingLeavesCount(pendingLeavesCount)
                .pendingAdvancesCount(pendingAdvancesCount)
                .pendingThumbLogsCount(pendingThumbLogsCount)
                .todayAttendanceSummary(buildTodayAttendanceSummary(companyId, today, totalActiveEmployees))
                .thisMonthPayrollStatus(buildThisMonthPayrollStatus(companyId, currentMonth))
                .build();
    }

    private HomeOpsTodayAttendanceSummaryResponse buildTodayAttendanceSummary(
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

        return HomeOpsTodayAttendanceSummaryResponse.builder()
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

    private HomeOpsPayrollStatusResponse buildThisMonthPayrollStatus(
            Long companyId, YearMonth month) {
        int monthValue = month.getMonthValue();
        int year = month.getYear();
        long generated = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYear(
                companyId, monthValue, year);
        long paid = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYearAndStatus(
                companyId, monthValue, year, PaymentStatus.PAID);
        long unpaid = salaryPaymentRepository.countByUser_CompanyIdAndMonthAndYearAndStatus(
                companyId, monthValue, year, PaymentStatus.DRAFT);

        return HomeOpsPayrollStatusResponse.builder()
                .month(monthValue)
                .year(year)
                .generated(generated)
                .paid(paid)
                .unpaid(unpaid)
                .build();
    }

    private HomeMeShiftResponse buildShift(Long companyId) {
        return companySettingsRepository.findByCompanyId(companyId)
                .map(settings -> HomeMeShiftResponse.builder()
                        .shiftStartTime(settings.getShiftStartTime())
                        .shiftEndTime(settings.getShiftEndTime())
                        .build())
                .orElse(null);
    }

    private HomeMeMonthSummaryResponse buildMonthSummary(Long userId, YearMonth month) {
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

        return HomeMeMonthSummaryResponse.builder()
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

    private String resolvePrimaryRole() {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            return ROLE_SUPERADMIN;
        }
        if (SecurityUtil.hasRole(ROLE_ADMIN)) {
            return ROLE_ADMIN;
        }
        if (SecurityUtil.hasRole(ROLE_HR)) {
            return ROLE_HR;
        }
        if (SecurityUtil.hasRole(ROLE_EMPLOYEE)) {
            return ROLE_EMPLOYEE;
        }
        List<String> roles = SecurityUtil.getCurrentUserRoles();
        if (roles.isEmpty()) {
            throw new UnauthorizedException("Not authenticated");
        }
        return roles.get(0);
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
