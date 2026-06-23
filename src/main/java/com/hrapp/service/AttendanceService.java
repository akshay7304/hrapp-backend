package com.hrapp.service;

import com.hrapp.dto.request.ManualAttendanceRequest;
import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.PunchResponse;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.AttendancePunch;
import com.hrapp.entity.Company;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.User;
import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.PunchType;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AttendancePunchRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Daily attendance with multiple IN/OUT punches per day.
 * Employee may check out at any time; after checkout they can check in again.
 * {@code workedHours} sums all paired IN→OUT durations (gaps between OUT and next IN are excluded).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendancePunchRepository attendancePunchRepository;
    private final UserRepository userRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;

    @Transactional
    public AttendanceResponse checkIn() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        if (holidayRepository.existsByCompanyIdAndHolidayDate(companyId, today)) {
            throw new BadRequestException("Cannot check in on holiday");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        if (isWeekOff(today, settings.getWeekOffDay())) {
            throw new BadRequestException("Cannot check in on week off");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Company company = user.getCompany();
        if (company == null) {
            throw new BadRequestException("User is not bound to a company");
        }

        Attendance attendance = attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .orElseGet(() -> Attendance.builder()
                        .user(user)
                        .company(company)
                        .attendanceDate(today)
                        .workedHours(BigDecimal.ZERO)
                        .overtimeHours(BigDecimal.ZERO)
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.MOBILE)
                        .isManual(false)
                        .isAutoCheckout(false)
                        .build());

        if (attendance.getId() != null) {
            Optional<AttendancePunch> lastPunch = getLastPunch(attendance.getId());
            if (!canCheckIn(attendance, lastPunch)) {
                throw new ConflictException("Already checked in. Please check out first.");
            }
        }

        boolean firstPunchOfDay = attendance.getId() == null
                || attendancePunchRepository.countByAttendanceId(attendance.getId()) == 0;
        if (firstPunchOfDay) {
            attendance.setCheckIn(now);
        }

        attendance = attendanceRepository.save(attendance);

        AttendancePunch punch = AttendancePunch.builder()
                .attendance(attendance)
                .user(user)
                .punchTime(now)
                .punchType(PunchType.IN)
                .source(AttendanceSource.MOBILE)
                .build();
        attendancePunchRepository.save(punch);

        if (!firstPunchOfDay) {
            attendance.setStatus(AttendanceStatus.PRESENT);
            attendance = attendanceRepository.save(attendance);
        }

        log.info("Check-in user={} date={} punchId={}", userId, today, punch.getId());
        return toResponse(attendance);
    }

    @Transactional
    public AttendanceResponse checkOut() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Attendance attendance = attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in found for today"));

        Optional<AttendancePunch> lastPunch = attendance.getId() != null
                ? getLastPunch(attendance.getId())
                : Optional.empty();
        if (!canCheckOut(attendance, lastPunch)) {
            throw new ConflictException("Not checked in. Please check in first.");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        User user = attendance.getUser();
        AttendancePunch punch = AttendancePunch.builder()
                .attendance(attendance)
                .user(user)
                .punchTime(now)
                .punchType(PunchType.OUT)
                .source(AttendanceSource.MOBILE)
                .build();
        attendancePunchRepository.save(punch);

        attendance.setCheckOut(now);

        List<AttendancePunch> punches = attendancePunchRepository
                .findByAttendanceIdOrderByPunchTimeAsc(attendance.getId());
        BigDecimal worked = calculateWorkedHours(punches);
        attendance.setWorkedHours(worked);
        attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
        attendance.setStatus(deriveStatus(worked, settings));

        attendance = attendanceRepository.save(attendance);
        log.info("Check-out user={} date={} worked={}h status={}",
                userId, today, worked, attendance.getStatus());
        return toResponse(attendance);
    }

    /**
     * Closes open attendance rows at shift end for employees who forgot to check out.
     * Intended to run nightly via {@link com.hrapp.config.MidnightAttendanceScheduler}.
     */
    @Transactional
    public int autoCheckoutMissed(Long companyId) {
        LocalDate today = LocalDate.now();

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        if (settings.getShiftEndTime() == null) {
            log.warn("Auto checkout skipped for companyId={} — shift_end_time not configured", companyId);
            return 0;
        }

        List<Attendance> candidates = attendanceRepository
                .findByCompanyIdAndAttendanceDateAndCheckInIsNotNull(companyId, today);

        int processed = 0;
        for (Attendance attendance : candidates) {
            Optional<AttendancePunch> lastPunch = getLastPunch(attendance.getId());
            if (!canCheckOut(attendance, lastPunch)) {
                continue;
            }

            LocalDateTime checkOutTime = today.atTime(settings.getShiftEndTime());
            User user = attendance.getUser();

            AttendancePunch punch = AttendancePunch.builder()
                    .attendance(attendance)
                    .user(user)
                    .punchTime(checkOutTime)
                    .punchType(PunchType.OUT)
                    .source(AttendanceSource.MANUAL)
                    .build();
            attendancePunchRepository.save(punch);

            attendance.setCheckOut(checkOutTime);
            attendance.setIsAutoCheckout(true);

            List<AttendancePunch> punches = attendancePunchRepository
                    .findByAttendanceIdOrderByPunchTimeAsc(attendance.getId());
            BigDecimal worked = calculateWorkedHours(punches);
            attendance.setWorkedHours(worked);
            attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
            attendance.setStatus(deriveStatus(worked, settings));

            attendanceRepository.save(attendance);
            processed++;
        }

        log.info("Auto checkout processed {} records for companyId={}", processed, companyId);
        return processed;
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
    public PageResponse<AttendanceResponse> getMyAttendanceHistory(int month, int year, Pageable pageable) {
        Long userId = requireCallerUserId();
        YearMonth ym = YearMonth.of(year, month);
        Pageable effective = applyDefaultSort(pageable, Sort.by("attendanceDate"));
        return PageResponse.from(
                attendanceRepository
                        .findByUserIdAndAttendanceDateBetween(
                                userId, ym.atDay(1), ym.atEndOfMonth(), effective)
                        .map(this::toResponse));
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

            attendance = attendanceRepository.save(attendance);

            attendancePunchRepository.deleteByAttendanceId(attendance.getId());

            attendancePunchRepository.save(AttendancePunch.builder()
                    .attendance(attendance)
                    .user(employee)
                    .punchTime(request.getCheckIn())
                    .punchType(PunchType.IN)
                    .source(AttendanceSource.MANUAL)
                    .build());
            attendancePunchRepository.save(AttendancePunch.builder()
                    .attendance(attendance)
                    .user(employee)
                    .punchTime(request.getCheckOut())
                    .punchType(PunchType.OUT)
                    .source(AttendanceSource.MANUAL)
                    .build());

            List<AttendancePunch> punches = attendancePunchRepository
                    .findByAttendanceIdOrderByPunchTimeAsc(attendance.getId());
            BigDecimal worked = calculateWorkedHours(punches);
            attendance.setWorkedHours(worked);
            attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
        } else {
            attendance.setWorkedHours(BigDecimal.ZERO);
            attendance.setOvertimeHours(BigDecimal.ZERO);
            if (attendance.getId() != null) {
                attendancePunchRepository.deleteByAttendanceId(attendance.getId());
            }
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

    private Optional<AttendancePunch> getLastPunch(Long attendanceId) {
        return attendancePunchRepository.findFirstByAttendanceIdOrderByPunchTimeDesc(attendanceId);
    }

    private boolean canCheckIn(Attendance attendance, Optional<AttendancePunch> lastPunch) {
        if (lastPunch.isEmpty()) {
            if (attendance.getCheckIn() == null) {
                return true;
            }
            return attendance.getCheckOut() != null;
        }
        return lastPunch.get().getPunchType() == PunchType.OUT;
    }

    private boolean canCheckOut(Attendance attendance, Optional<AttendancePunch> lastPunch) {
        if (lastPunch.isEmpty()) {
            return attendance.getCheckIn() != null && attendance.getCheckOut() == null;
        }
        return lastPunch.get().getPunchType() == PunchType.IN;
    }

    private BigDecimal calculateWorkedHours(List<AttendancePunch> punches) {
        List<AttendancePunch> sorted = punches.stream()
                .sorted(Comparator.comparing(AttendancePunch::getPunchTime))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime openIn = null;
        for (AttendancePunch punch : sorted) {
            if (punch.getPunchType() == PunchType.IN) {
                openIn = punch.getPunchTime();
            } else if (punch.getPunchType() == PunchType.OUT && openIn != null) {
                total = total.add(calculateHours(openIn, punch.getPunchTime()));
                openIn = null;
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private int countCompleteSessions(List<AttendancePunch> punches) {
        List<AttendancePunch> sorted = punches.stream()
                .sorted(Comparator.comparing(AttendancePunch::getPunchTime))
                .toList();

        int sessions = 0;
        LocalDateTime openIn = null;
        for (AttendancePunch punch : sorted) {
            if (punch.getPunchType() == PunchType.IN) {
                openIn = punch.getPunchTime();
            } else if (punch.getPunchType() == PunchType.OUT && openIn != null) {
                sessions++;
                openIn = null;
            }
        }
        return sessions;
    }

    private boolean isCheckedIn(List<AttendancePunch> punches, Attendance attendance) {
        if (punches.isEmpty()) {
            return attendance.getCheckIn() != null && attendance.getCheckOut() == null;
        }
        AttendancePunch last = punches.get(punches.size() - 1);
        return last.getPunchType() == PunchType.IN;
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

    private PunchResponse toPunchResponse(AttendancePunch punch) {
        return PunchResponse.builder()
                .id(punch.getId())
                .punchTime(punch.getPunchTime())
                .punchType(punch.getPunchType() != null ? punch.getPunchType().name() : null)
                .source(punch.getSource() != null ? punch.getSource().name() : null)
                .build();
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        User user = attendance.getUser();
        List<AttendancePunch> punches = attendance.getId() != null
                ? attendancePunchRepository.findByAttendanceIdOrderByPunchTimeAsc(attendance.getId())
                : new ArrayList<>();

        List<PunchResponse> punchResponses = punches.stream()
                .map(this::toPunchResponse)
                .toList();

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
                .isAutoCheckout(attendance.getIsAutoCheckout())
                .manualReason(attendance.getManualReason())
                .punches(punchResponses)
                .totalSessions(countCompleteSessions(punches))
                .isCheckedIn(isCheckedIn(punches, attendance))
                .build();
    }
}
