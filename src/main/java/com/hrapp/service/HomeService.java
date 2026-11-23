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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        Object[] row = attendanceRepository.summarizeCompanyDay(companyId, today);
        // Hibernate may return the scalar array directly, or wrap as Object[]{ Object[] }.
        if (row != null && row.length == 1 && row[0] instanceof Object[] nested) {
            row = nested;
        }
        int present = toInt(row, 0);
        int absent = toInt(row, 1);
        int halfDay = toInt(row, 2);
        int onLeave = toInt(row, 3);
        int weekOff = toInt(row, 4);
        int holiday = toInt(row, 5);
        int checkedInToday = toInt(row, 6);
        int checkedInNow = toInt(row, 7);
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
        long generated = 0;
        long paid = 0;
        long unpaid = 0;
        for (Object[] row : salaryPaymentRepository.countStatusesByCompanyMonthYear(
                companyId, monthValue, year)) {
            PaymentStatus status = (PaymentStatus) row[0];
            long count = ((Number) row[1]).longValue();
            generated += count;
            if (status == PaymentStatus.PAID) {
                paid = count;
            } else if (status == PaymentStatus.DRAFT) {
                unpaid = count;
            }
        }

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
        Map<AttendanceStatus, Integer> counts = new EnumMap<>(AttendanceStatus.class);
        for (Object[] row : attendanceRepository.countStatusByUserIdAndAttendanceDateBetween(
                userId, month.atDay(1), month.atEndOfMonth())) {
            if (row[0] == null) {
                continue;
            }
            counts.put((AttendanceStatus) row[0], ((Number) row[1]).intValue());
        }

        return HomeMeMonthSummaryResponse.builder()
                .month(month.getMonthValue())
                .year(month.getYear())
                .presentDays(counts.getOrDefault(AttendanceStatus.PRESENT, 0))
                .absentDays(counts.getOrDefault(AttendanceStatus.ABSENT, 0))
                .halfDays(counts.getOrDefault(AttendanceStatus.HALF_DAY, 0))
                .onLeaveDays(counts.getOrDefault(AttendanceStatus.ON_LEAVE, 0))
                .weekOffDays(counts.getOrDefault(AttendanceStatus.WEEK_OFF, 0))
                .holidayDays(counts.getOrDefault(AttendanceStatus.HOLIDAY, 0))
                .build();
    }

    private static int toInt(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return 0;
        }
        return ((Number) row[index]).intValue();
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
