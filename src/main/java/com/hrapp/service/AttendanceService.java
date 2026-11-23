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
import com.hrapp.util.AttendanceDuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Daily attendance with multiple IN/OUT punches per day.
 * Employee may check out at any time; after checkout they can check in again.
 * Worked time is the exact second-count of paired IN→OUT durations (gaps excluded),
 * returned as {@code HH:MM:SS}. A span of 08:29:57 is stored as 30597 seconds.
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
                        .source(AttendanceSource.WEB)
                        .isManual(false)
                        .isAutoCheckout(false)
                        .build());

        List<AttendancePunch> existingPunches = attendance.getId() != null
                ? attendancePunchRepository.findByAttendanceIdOrderByPunchTimeAsc(attendance.getId())
                : List.of();
        Optional<AttendancePunch> lastPunch = existingPunches.isEmpty()
                ? Optional.empty()
                : Optional.of(existingPunches.get(existingPunches.size() - 1));
        if (attendance.getId() != null && !canCheckIn(attendance, lastPunch)) {
            throw new ConflictException("Already checked in. Please check out first.");
        }

        boolean firstPunchOfDay = existingPunches.isEmpty();
        if (firstPunchOfDay) {
            attendance.setCheckIn(now);
        }

        attendance = attendanceRepository.save(attendance);

        AttendancePunch punch = AttendancePunch.builder()
                .attendance(attendance)
                .user(user)
                .punchTime(now)
                .punchType(PunchType.IN)
                .source(AttendanceSource.WEB)
                .build();
        punch = attendancePunchRepository.save(punch);

        if (!firstPunchOfDay) {
            attendance.setStatus(AttendanceStatus.PRESENT);
            attendance = attendanceRepository.save(attendance);
        }

        List<AttendancePunch> punchesForResponse = new ArrayList<>(existingPunches);
        punchesForResponse.add(punch);

        log.info("Check-in user={} date={} punchId={}", userId, today, punch.getId());
        return toResponse(attendance, punchesForResponse);
    }

    @Transactional
    public AttendanceResponse checkOut() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Attendance attendance = attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in found for today"));

        List<AttendancePunch> existingPunches = attendance.getId() != null
                ? attendancePunchRepository.findByAttendanceIdOrderByPunchTimeAsc(attendance.getId())
                : List.of();
        Optional<AttendancePunch> lastPunch = existingPunches.isEmpty()
                ? Optional.empty()
                : Optional.of(existingPunches.get(existingPunches.size() - 1));
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
                .source(AttendanceSource.WEB)
                .build();
        punch = attendancePunchRepository.save(punch);

        attendance.setCheckOut(now);

        List<AttendancePunch> punches = new ArrayList<>(existingPunches);
        punches.add(punch);
        applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches), settings);

        attendance = attendanceRepository.save(attendance);
        log.info("Check-out user={} date={} worked={} status={}",
                userId, today, attendance.getWorkedSeconds(), attendance.getStatus());
        return toResponse(attendance, punches);
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
            LocalDateTime checkIn = attendance.getCheckIn();
            // Someone who punched in at/after shift end cannot be closed at
            // shift end — that would produce 0 (or negative) worked hours.
            if (checkIn != null && !checkOutTime.isAfter(checkIn)) {
                log.debug("Auto checkout skipped attendanceId={} — check-in {} is at/after shift end {}",
                        attendance.getId(), checkIn, checkOutTime);
                continue;
            }

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
            applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches), settings);

            attendanceRepository.save(attendance);
            processed++;
        }

        log.info("Auto checkout processed {} records for companyId={}", processed, companyId);
        return processed;
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance() {
        return findTodayAttendanceOptional()
                .orElseThrow(() -> new ResourceNotFoundException("No attendance recorded for today"));
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceResponse> findTodayAttendanceOptional() {
        Long userId = requireCallerUserId();
        LocalDate today = LocalDate.now();
        return attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .map(this::toResponse);
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
            applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches), settings);
            attendance.setStatus(request.getStatus());
        } else {
            attendance.setWorkedHours(BigDecimal.ZERO);
            attendance.setOvertimeHours(BigDecimal.ZERO);
            attendance.setWorkedSeconds(0L);
            attendance.setOvertimeSeconds(0L);
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
        return com.hrapp.util.WeekOffSupport.isWeekOff(date, weekOffDay);
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

    private void applyDuration(Attendance attendance, long workedSeconds, CompanySettings settings) {
        long overtimeSeconds = AttendanceDuration.overtimeSeconds(
                workedSeconds, settings.getOvertimeAfterHours());
        attendance.setWorkedSeconds(workedSeconds);
        attendance.setOvertimeSeconds(overtimeSeconds);
        attendance.setWorkedHours(AttendanceDuration.toPayrollHours(workedSeconds));
        attendance.setOvertimeHours(AttendanceDuration.toPayrollHours(overtimeSeconds));
        attendance.setStatus(AttendanceDuration.deriveStatus(
                workedSeconds, settings.getShiftHours(), settings.getHalfDayHours()));
    }

    private long overtimeFromSettings(Attendance attendance, long workedSeconds) {
        Long companyId = attendance.getCompany() != null ? attendance.getCompany().getId() : null;
        if (companyId == null) {
            return safeSeconds(attendance.getOvertimeSeconds());
        }
        BigDecimal threshold = companySettingsRepository.findByCompanyId(companyId)
                .map(CompanySettings::getOvertimeAfterHours)
                .orElse(null);
        return AttendanceDuration.overtimeSeconds(workedSeconds, threshold);
    }

    private long safeSeconds(Long seconds) {
        return seconds == null ? 0L : Math.max(seconds, 0L);
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

    private PunchResponse toPunchResponse(AttendancePunch punch) {
        return PunchResponse.builder()
                .id(punch.getId())
                .punchTime(punch.getPunchTime())
                .punchType(punch.getPunchType() != null ? punch.getPunchType().name() : null)
                .source(punch.getSource() != null ? punch.getSource().name() : null)
                .build();
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        List<AttendancePunch> punches = attendance.getId() != null
                ? attendancePunchRepository.findByAttendanceIdOrderByPunchTimeAsc(attendance.getId())
                : new ArrayList<>();
        return toResponse(attendance, punches);
    }

    private AttendanceResponse toResponse(Attendance attendance, List<AttendancePunch> punches) {
        User user = attendance.getUser();
        List<PunchResponse> punchResponses = punches.stream()
                .map(this::toPunchResponse)
                .toList();

        boolean fromPunches = !punches.isEmpty();
        long workedSeconds = fromPunches
                ? AttendanceDuration.sumPairedSeconds(punches)
                : safeSeconds(attendance.getWorkedSeconds());
        long overtimeSeconds = fromPunches
                ? overtimeFromSettings(attendance, workedSeconds)
                : safeSeconds(attendance.getOvertimeSeconds());

        return AttendanceResponse.builder()
                .id(attendance.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .attendanceDate(attendance.getAttendanceDate())
                .checkIn(attendance.getCheckIn())
                .checkOut(attendance.getCheckOut())
                .workedHours(AttendanceDuration.format(workedSeconds))
                .overtimeHours(AttendanceDuration.format(overtimeSeconds))
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
