package com.hrapp.service;

import com.hrapp.dto.request.ManualAttendanceRequest;
import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.Company;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.User;
import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Daily attendance lifecycle:
 * <ol>
 *   <li>{@link #checkIn()} — creates today's row, marking holiday / week-off
 *       statuses automatically when applicable.</li>
 *   <li>{@link #checkOut()} — closes the row, derives worked / overtime hours
 *       and final status from {@code CompanySettings}.</li>
 * </ol>
 * Admin / HR can also create or override rows via {@link #markManualAttendance}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;

    @Transactional
    public AttendanceResponse checkIn() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = LocalDate.now();

        if (attendanceRepository.existsByUserIdAndAttendanceDate(userId, today)) {
            throw new ConflictException("Already checked in today");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Company company = user.getCompany();
        if (company == null) {
            throw new BadRequestException("User is not bound to a company");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        AttendanceStatus status;
        LocalDateTime checkInTime = null;

        if (holidayRepository.existsByCompanyIdAndHolidayDate(companyId, today)) {
            status = AttendanceStatus.HOLIDAY;
        } else if (isWeekOff(today, settings.getWeekOffDay())) {
            status = AttendanceStatus.WEEK_OFF;
        } else {
            status = AttendanceStatus.PRESENT;
            checkInTime = LocalDateTime.now();
        }

        Attendance attendance = Attendance.builder()
                .user(user)
                .company(company)
                .attendanceDate(today)
                .checkIn(checkInTime)
                .workedHours(BigDecimal.ZERO)
                .overtimeHours(BigDecimal.ZERO)
                .status(status)
                .source(AttendanceSource.MOBILE)
                .isManual(false)
                .build();
        attendance = attendanceRepository.save(attendance);

        log.info("Check-in user={} date={} status={}", userId, today, status);
        return toResponse(attendance);
    }

    @Transactional
    public AttendanceResponse checkOut() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = LocalDate.now();

        Attendance attendance = attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in found for today"));

        if (attendance.getCheckOut() != null) {
            throw new ConflictException("Already checked out today");
        }
        if (attendance.getCheckIn() == null) {
            throw new BadRequestException("Cannot check out — no check-in recorded for today");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        LocalDateTime checkOutTime = LocalDateTime.now();
        attendance.setCheckOut(checkOutTime);

        BigDecimal worked = calculateHours(attendance.getCheckIn(), checkOutTime);
        attendance.setWorkedHours(worked);
        attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
        attendance.setStatus(deriveStatus(worked, settings));

        attendance = attendanceRepository.save(attendance);
        log.info("Check-out user={} date={} worked={}h status={}",
                userId, today, worked, attendance.getStatus());
        return toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance() {
        Long userId = requireCallerUserId();
        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No attendance recorded for today"));
        return toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getMyAttendanceHistory(int month, int year) {
        Long userId = requireCallerUserId();
        YearMonth ym = YearMonth.of(year, month);
        return attendanceRepository
                .findByUserIdAndAttendanceDateBetween(userId, ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .sorted(Comparator.comparing(Attendance::getAttendanceDate))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getCompanyAttendanceToday(Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Pageable effective = applyDefaultSort(pageable, Sort.by("user.fullName"));
        return PageResponse.from(
                attendanceRepository
                        .findByCompanyIdAndAttendanceDate(companyId, LocalDate.now(), effective)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getEmployeeAttendanceHistory(
            Long employeeId, int month, int year, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(employee, companyId);

        YearMonth ym = YearMonth.of(year, month);
        Pageable effective = applyDefaultSort(pageable, Sort.by("attendanceDate"));
        return PageResponse.from(
                attendanceRepository
                        .findByUserIdAndAttendanceDateBetween(
                                employeeId, ym.atDay(1), ym.atEndOfMonth(), effective)
                        .map(this::toResponse));
    }

    @Transactional
    public AttendanceResponse markManualAttendance(ManualAttendanceRequest request) {
        Long companyId = requireCallerCompanyId();
        Long markerId = requireCallerUserId();

        User employee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(employee, companyId);

        User marker = userRepository.findById(markerId)
                .orElseThrow(() -> new ResourceNotFoundException("Marker user not found"));

        Company company = employee.getCompany();

        Attendance attendance = attendanceRepository
                .findByUserIdAndAttendanceDate(request.getUserId(), request.getAttendanceDate())
                .orElseGet(() -> Attendance.builder()
                        .user(employee)
                        .company(company)
                        .attendanceDate(request.getAttendanceDate())
                        .workedHours(BigDecimal.ZERO)
                        .overtimeHours(BigDecimal.ZERO)
                        .build());

        attendance.setCheckIn(request.getCheckIn());
        attendance.setCheckOut(request.getCheckOut());
        attendance.setStatus(request.getStatus());
        attendance.setSource(AttendanceSource.MANUAL);
        attendance.setIsManual(true);
        attendance.setManualReason(request.getManualReason());
        attendance.setMarkedBy(marker);

        if (request.getCheckIn() != null && request.getCheckOut() != null) {
            CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                    .orElseThrow(() -> new BadRequestException("Company settings not configured"));
            BigDecimal worked = calculateHours(request.getCheckIn(), request.getCheckOut());
            attendance.setWorkedHours(worked);
            attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
        } else {
            attendance.setWorkedHours(BigDecimal.ZERO);
            attendance.setOvertimeHours(BigDecimal.ZERO);
        }

        attendance = attendanceRepository.save(attendance);
        log.info("Manual attendance marked by={} for user={} date={} status={}",
                markerId, request.getUserId(), request.getAttendanceDate(), request.getStatus());
        return toResponse(attendance);
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

    /**
     * Apply a sensible default sort when the caller didn't supply one.
     * Preserves the previously-hardcoded ordering of list endpoints now that
     * pagination has replaced the explicit {@code ORDER BY} in the JPQL.
     */
    private Pageable applyDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private void ensureSameCompany(User employee, Long callerCompanyId) {
        Long employeeCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        if (!callerCompanyId.equals(employeeCompanyId)) {
            log.warn("Cross-company attendance access blocked — caller={} target={}",
                    callerCompanyId, employeeCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
    }

    private boolean isWeekOff(LocalDate date, String weekOffDay) {
        if (weekOffDay == null || weekOffDay.isBlank()) {
            return false;
        }
        try {
            DayOfWeek configured = DayOfWeek.valueOf(weekOffDay.trim().toUpperCase());
            return date.getDayOfWeek() == configured;
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid week-off day configured: '{}'", weekOffDay);
            return false;
        }
    }

    private BigDecimal calculateHours(LocalDateTime from, LocalDateTime to) {
        long minutes = ChronoUnit.MINUTES.between(from, to);
        if (minutes < 0) {
            minutes = 0;
        }
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateOvertime(BigDecimal worked, BigDecimal threshold) {
        if (worked == null || threshold == null) {
            return BigDecimal.ZERO;
        }
        return worked.compareTo(threshold) > 0
                ? worked.subtract(threshold).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private AttendanceStatus deriveStatus(BigDecimal worked, CompanySettings settings) {
        BigDecimal shiftHours = settings.getShiftHours();
        BigDecimal halfDayHours = settings.getHalfDayHours();
        if (shiftHours != null && worked.compareTo(shiftHours) >= 0) {
            return AttendanceStatus.PRESENT;
        }
        if (halfDayHours != null && worked.compareTo(halfDayHours) >= 0) {
            return AttendanceStatus.HALF_DAY;
        }
        return AttendanceStatus.ABSENT;
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        User user = attendance.getUser();
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .attendanceDate(attendance.getAttendanceDate())
                .checkIn(attendance.getCheckIn())
                .checkOut(attendance.getCheckOut())
                .workedHours(attendance.getWorkedHours())
                .overtimeHours(attendance.getOvertimeHours())
                .status(attendance.getStatus() != null ? attendance.getStatus().name() : null)
                .source(attendance.getSource() != null ? attendance.getSource().name() : null)
                .isManual(attendance.getIsManual())
                .manualReason(attendance.getManualReason())
                .build();
    }
}
