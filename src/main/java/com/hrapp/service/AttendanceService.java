package com.hrapp.service;

import com.hrapp.util.AppTime;

import com.hrapp.dto.request.ManualAttendanceRequest;
import com.hrapp.dto.request.SetOvertimeRequest;
import com.hrapp.dto.response.AttendanceResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.PunchResponse;
import com.hrapp.dto.response.ResolvedShiftResponse;
import com.hrapp.dto.response.ShiftResponse;
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
import com.hrapp.repository.AttendanceJdbcRepository;
import com.hrapp.repository.AttendancePunchRepository;
import com.hrapp.repository.AttendancePunchRow;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.AttendanceRow;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import com.hrapp.util.AttendanceDuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final int MAX_PAGE_SIZE = 100;

    private final AttendanceRepository attendanceRepository;
    private final AttendancePunchRepository attendancePunchRepository;
    private final AttendanceJdbcRepository attendanceJdbcRepository;
    private final UserRepository userRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;
    private final ShiftAssignmentService shiftAssignmentService;

    @Transactional
    public AttendanceResponse checkIn() {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();
        LocalDate today = AppTime.today();
        LocalDateTime now = AppTime.now();

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
            try {
                attendance.setIsLate(isLateAgainstShift(now, today,
                        resolveShift(companyId, userId, today)));
            } catch (Exception ex) {
                log.debug("isLate not set on check-in user={}: {}", userId, ex.getMessage());
            }
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
        LocalDate today = AppTime.today();
        LocalDateTime now = AppTime.now();

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
        applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches),
                resolveShift(companyId, userId, today));

        attendance = attendanceRepository.save(attendance);
        log.info("Check-out user={} date={} worked={} status={}",
                userId, today, attendance.getWorkedSeconds(), attendance.getStatus());
        return toResponse(attendance, punches);
    }

    /**
     * Closes open attendance rows at each employee's resolved shift end.
     * Intended to run nightly via {@link com.hrapp.config.MidnightAttendanceScheduler}.
     */
    @Transactional
    public int autoCheckoutMissed(Long companyId) {
        LocalDate today = AppTime.today();

        List<Attendance> candidates = attendanceRepository
                .findByCompanyIdAndAttendanceDateAndCheckInIsNotNull(companyId, today);

        int processed = 0;
        for (Attendance attendance : candidates) {
            Optional<AttendancePunch> lastPunch = getLastPunch(attendance.getId());
            if (!canCheckOut(attendance, lastPunch)) {
                continue;
            }

            User user = attendance.getUser();
            ResolvedShiftResponse resolved = resolveShift(companyId, user.getId(), today);
            LocalDateTime checkOutTime = shiftEndDateTime(today, resolved);
            if (checkOutTime == null) {
                log.debug("Auto checkout skipped attendanceId={} — no resolved shift end",
                        attendance.getId());
                continue;
            }

            LocalDateTime checkIn = attendance.getCheckIn();
            // Someone who punched in at/after shift end cannot be closed at
            // shift end — that would produce 0 (or negative) worked hours.
            if (checkIn != null && !checkOutTime.isAfter(checkIn)) {
                log.debug("Auto checkout skipped attendanceId={} — check-in {} is at/after shift end {}",
                        attendance.getId(), checkIn, checkOutTime);
                continue;
            }

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
            applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches), resolved);

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
        LocalDate today = AppTime.today();
        return attendanceRepository.findByUserIdAndAttendanceDate(userId, today)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getMyAttendanceHistory(int month, int year, int page, int size) {
        validateAttendanceListPage(page, size);
        validateAttendanceMonthYear(month, year);
        Long userId = requireCallerUserId();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        long offset = (long) page * size;
        List<AttendanceRow> rows = attendanceJdbcRepository
                .getAttendanceHistoryByUser(userId, from, to, size, offset);
        long total = attendanceJdbcRepository.getAttendanceHistoryByUserCount(userId, from, to);
        return PageResponse.of(toAttendanceResponsesFromRows(rows), page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getCompanyAttendanceToday(int page, int size) {
        validateAttendanceListPage(page, size);
        Long companyId = requireCallerCompanyId();
        LocalDate today = AppTime.today();
        long offset = (long) page * size;
        List<AttendanceRow> rows = attendanceJdbcRepository
                .getCompanyAttendanceByDate(companyId, today, size, offset);
        long total = attendanceJdbcRepository.getCompanyAttendanceByDateCount(companyId, today);
        return PageResponse.of(toAttendanceResponsesFromRows(rows), page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getEmployeeAttendanceHistory(
            Long employeeId, int month, int year, int page, int size) {
        validateAttendanceListPage(page, size);
        validateAttendanceMonthYear(month, year);
        Long companyId = requireCallerCompanyId();
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureEmployeeBelongsToCallerCompany(employee, companyId);

        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        long offset = (long) page * size;
        List<AttendanceRow> rows = attendanceJdbcRepository
                .getAttendanceHistoryByUser(employeeId, from, to, size, offset);
        long total = attendanceJdbcRepository.getAttendanceHistoryByUserCount(employeeId, from, to);
        return PageResponse.of(toAttendanceResponsesFromRows(rows), page, size, total);
    }

    /**
     * Resolves the company for an auto-checkout run from the caller role
     * and optional {@code companyId} query param.
     */
    @Transactional
    public int autoCheckoutMissedForCaller(Long requestedCompanyId) {
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        Long effectiveCompanyId;
        if (SecurityUtil.hasRole("SUPERADMIN")) {
            effectiveCompanyId = requestedCompanyId != null ? requestedCompanyId : callerCompanyId;
            if (effectiveCompanyId == null) {
                throw new BadRequestException("companyId is required for SUPERADMIN auto checkout");
            }
        } else {
            if (callerCompanyId == null) {
                throw new UnauthorizedException("Caller is not bound to a company");
            }
            if (requestedCompanyId != null && !requestedCompanyId.equals(callerCompanyId)) {
                throw new UnauthorizedException("Cannot run auto checkout for another company");
            }
            effectiveCompanyId = callerCompanyId;
        }
        return autoCheckoutMissed(effectiveCompanyId);
    }

    @Transactional
    public AttendanceResponse markManualAttendance(ManualAttendanceRequest request) {
        Long companyId = requireCallerCompanyId();
        Long markerId = requireCallerUserId();

        User employee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureEmployeeBelongsToCallerCompany(employee, companyId);

        User marker = userRepository.findById(markerId)
                .orElseThrow(() -> new ResourceNotFoundException("Marker user not found"));

        Company company = employee.getCompany();
        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));
        LocalDate date = request.getAttendanceDate();
        if (holidayRepository.existsByCompanyIdAndHolidayDate(companyId, date)) {
            throw new BadRequestException("Cannot mark attendance on holiday");
        }
        if (isWeekOff(date, settings.getWeekOffDay())) {
            throw new BadRequestException("Cannot mark attendance on week off");
        }

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
            ResolvedShiftResponse resolved = resolveShift(
                    companyId, employee.getId(), request.getAttendanceDate());
            attendance.setIsLate(isLateAgainstShift(
                    request.getCheckIn(), request.getAttendanceDate(), resolved));
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
            applyDuration(attendance, AttendanceDuration.sumPairedSeconds(punches), resolved);
            // Manual mark keeps HR-chosen status after duration calc
            attendance.setStatus(request.getStatus());
        } else {
            attendance.setWorkedHours(BigDecimal.ZERO);
            attendance.setOvertimeHours(BigDecimal.ZERO);
            attendance.setWorkedSeconds(0L);
            attendance.setOvertimeSeconds(0L);
            attendance.setIsLate(null);
            if (attendance.getId() != null) {
                attendancePunchRepository.deleteByAttendanceId(attendance.getId());
            }
        }

        attendance = attendanceRepository.save(attendance);
        log.info("Manual attendance marked by={} for user={} date={} status={}",
                markerId, request.getUserId(), request.getAttendanceDate(), request.getStatus());
        return toResponse(attendance);
    }

    /**
     * Manual OT only — never derived from worked hours vs shift threshold.
     */
    @Transactional
    public AttendanceResponse setOvertime(Long attendanceId, SetOvertimeRequest request) {
        Long companyId = requireCallerCompanyId();
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found"));
        User user = attendance.getUser();
        ensureEmployeeBelongsToCallerCompany(user, companyId);

        BigDecimal hours = request.getOvertimeHours() != null
                ? request.getOvertimeHours()
                : BigDecimal.ZERO;
        if (hours.signum() < 0) {
            throw new BadRequestException("overtimeHours cannot be negative");
        }
        long seconds = hours.multiply(BigDecimal.valueOf(3600))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();
        attendance.setOvertimeSeconds(seconds);
        attendance.setOvertimeHours(hours.setScale(2, java.math.RoundingMode.HALF_UP));
        attendance = attendanceRepository.save(attendance);
        log.info("Manual OT set attendanceId={} hours={} by company={}",
                attendanceId, hours, companyId);
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

    private void ensureEmployeeBelongsToCallerCompany(User employee, Long callerCompanyId) {
        Long employeeCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        if (!callerCompanyId.equals(employeeCompanyId)) {
            log.warn("Cross-company attendance access blocked — caller={} target={}",
                    callerCompanyId, employeeCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
    }

    private static void validateAttendanceListPage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static void validateAttendanceMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new BadRequestException("Month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("Year is out of range");
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

    /**
     * Applies worked duration and PRESENT/HALF_DAY/ABSENT from the resolved shift.
     * Overtime is never auto-calculated — OT is manual-only (Phase 3 lock).
     */
    private void applyDuration(Attendance attendance, long workedSeconds, ResolvedShiftResponse resolved) {
        attendance.setWorkedSeconds(workedSeconds);
        attendance.setOvertimeSeconds(0L);
        attendance.setWorkedHours(AttendanceDuration.toPayrollHours(workedSeconds));
        attendance.setOvertimeHours(BigDecimal.ZERO.setScale(2));

        ShiftResponse shift = resolved != null ? resolved.getShift() : null;
        BigDecimal fullDay = shift != null ? shift.getFullDayHours() : null;
        BigDecimal halfDay = shift != null ? shift.getHalfDayHours() : null;
        attendance.setStatus(AttendanceDuration.deriveStatus(workedSeconds, fullDay, halfDay));
    }

    private ResolvedShiftResponse resolveShift(Long companyId, Long userId, LocalDate date) {
        return shiftAssignmentService.resolveForSystem(companyId, userId, date);
    }

    private LocalDateTime shiftEndDateTime(LocalDate attendanceDate, ResolvedShiftResponse resolved) {
        if (resolved == null || resolved.getShift() == null || resolved.getShift().getEndTime() == null) {
            return null;
        }
        ShiftResponse shift = resolved.getShift();
        LocalTime end = shift.getEndTime();
        if (Boolean.TRUE.equals(shift.getIsOvernight())) {
            return attendanceDate.plusDays(1).atTime(end);
        }
        return attendanceDate.atTime(end);
    }

    private boolean isLateAgainstShift(LocalDateTime checkIn, LocalDate attendanceDate,
                                       ResolvedShiftResponse resolved) {
        if (checkIn == null || resolved == null || resolved.getShift() == null
                || resolved.getShift().getStartTime() == null) {
            return false;
        }
        int grace = resolved.getShift().getGraceLateMinutes() != null
                ? resolved.getShift().getGraceLateMinutes()
                : 0;
        LocalDateTime lateAfter = attendanceDate
                .atTime(resolved.getShift().getStartTime())
                .plusMinutes(grace);
        return checkIn.isAfter(lateAfter);
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
        return toResponse(attendance, punches, false);
    }

    private AttendanceResponse toResponse(Attendance attendance, List<AttendancePunch> punches) {
        return toResponse(attendance, punches, false);
    }

    /**
     * List/history mapping from JDBC rows: punches batched once;
     * {@code isLate} from the stored column (no shift resolve on the read path).
     */
    private List<AttendanceResponse> toAttendanceResponsesFromRows(List<AttendanceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> attendanceIds = rows.stream().map(AttendanceRow::id).filter(Objects::nonNull).toList();
        Map<Long, List<AttendancePunchRow>> punchesByAttendance =
                attendanceJdbcRepository.getAttendancePunchesByAttendanceIds(attendanceIds).stream()
                        .collect(Collectors.groupingBy(AttendancePunchRow::attendanceId));

        List<AttendanceResponse> out = new ArrayList<>(rows.size());
        for (AttendanceRow row : rows) {
            List<AttendancePunchRow> punchRows =
                    punchesByAttendance.getOrDefault(row.id(), List.of());
            out.add(toAttendanceResponseFromRow(row, punchRows));
        }
        return out;
    }

    private AttendanceResponse toAttendanceResponseFromRow(
            AttendanceRow row, List<AttendancePunchRow> punchRows) {
        List<PunchResponse> punchResponses = punchRows.stream()
                .map(this::toPunchResponseFromRow)
                .toList();

        boolean fromPunches = !punchRows.isEmpty();
        long workedSeconds = fromPunches
                ? sumPairedSecondsFromRows(punchRows)
                : safeSeconds(row.workedSeconds());
        long overtimeSeconds = safeSeconds(row.overtimeSeconds());

        return AttendanceResponse.builder()
                .id(row.id())
                .userId(row.userId())
                .fullName(row.fullName())
                .empCode(row.empCode())
                .attendanceDate(row.attendanceDate())
                .checkIn(row.checkIn())
                .checkOut(row.checkOut())
                .workedHours(AttendanceDuration.format(workedSeconds))
                .overtimeHours(AttendanceDuration.format(overtimeSeconds))
                .status(row.status())
                .source(row.source())
                .isManual(row.isManual())
                .isAutoCheckout(row.isAutoCheckout())
                .isLate(row.isLate())
                .manualReason(row.manualReason())
                .punches(punchResponses)
                .totalSessions(countCompleteSessionsFromRows(punchRows))
                .isCheckedIn(isCheckedInFromRows(punchRows, row))
                .build();
    }

    private PunchResponse toPunchResponseFromRow(AttendancePunchRow punch) {
        return PunchResponse.builder()
                .id(punch.id())
                .punchTime(punch.punchTime())
                .punchType(punch.punchType())
                .source(punch.source())
                .build();
    }

    private static long sumPairedSecondsFromRows(List<AttendancePunchRow> punches) {
        List<AttendancePunchRow> sorted = punches.stream()
                .sorted(Comparator.comparing(AttendancePunchRow::punchTime))
                .toList();
        long total = 0L;
        LocalDateTime openIn = null;
        for (AttendancePunchRow punch : sorted) {
            if ("IN".equals(punch.punchType())) {
                openIn = punch.punchTime();
            } else if ("OUT".equals(punch.punchType()) && openIn != null) {
                total += AttendanceDuration.between(openIn, punch.punchTime());
                openIn = null;
            }
        }
        return total;
    }

    private static int countCompleteSessionsFromRows(List<AttendancePunchRow> punches) {
        List<AttendancePunchRow> sorted = punches.stream()
                .sorted(Comparator.comparing(AttendancePunchRow::punchTime))
                .toList();
        int sessions = 0;
        LocalDateTime openIn = null;
        for (AttendancePunchRow punch : sorted) {
            if ("IN".equals(punch.punchType())) {
                openIn = punch.punchTime();
            } else if ("OUT".equals(punch.punchType()) && openIn != null) {
                sessions++;
                openIn = null;
            }
        }
        return sessions;
    }

    private static boolean isCheckedInFromRows(List<AttendancePunchRow> punches, AttendanceRow row) {
        if (punches.isEmpty()) {
            return row.checkIn() != null && row.checkOut() == null;
        }
        AttendancePunchRow last = punches.get(punches.size() - 1);
        return "IN".equals(last.punchType());
    }

    private AttendanceResponse toResponse(
            Attendance attendance, List<AttendancePunch> punches, boolean listView) {
        User user = attendance.getUser();
        List<PunchResponse> punchResponses = punches.stream()
                .map(this::toPunchResponse)
                .toList();

        boolean fromPunches = !punches.isEmpty();
        long workedSeconds = fromPunches
                ? AttendanceDuration.sumPairedSeconds(punches)
                : safeSeconds(attendance.getWorkedSeconds());
        // Stored OT only — never recompute from shift/settings (manual OT)
        long overtimeSeconds = safeSeconds(attendance.getOvertimeSeconds());

        Boolean late = attendance.getIsLate();
        // Detail/today: compute once if not yet persisted (legacy rows).
        if (!listView && late == null && attendance.getCheckIn() != null
                && user != null && user.getCompany() != null) {
            try {
                ResolvedShiftResponse resolved = resolveShift(
                        user.getCompany().getId(), user.getId(), attendance.getAttendanceDate());
                late = isLateAgainstShift(
                        attendance.getCheckIn(), attendance.getAttendanceDate(), resolved);
            } catch (Exception ex) {
                log.debug("Late flag skipped for attendanceId={}: {}", attendance.getId(), ex.getMessage());
            }
        }

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
                .isLate(late)
                .manualReason(attendance.getManualReason())
                .punches(punchResponses)
                .totalSessions(countCompleteSessions(punches))
                .isCheckedIn(isCheckedIn(punches, attendance))
                .build();
    }
}
