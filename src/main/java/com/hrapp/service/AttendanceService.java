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

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
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

    private final AttendanceRepository attendanceRepository;
    private final AttendancePunchRepository attendancePunchRepository;
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
    public PageResponse<AttendanceResponse> getMyAttendanceHistory(int month, int year, Pageable pageable) {
        Long userId = requireCallerUserId();
        YearMonth ym = YearMonth.of(year, month);
        Pageable effective = applyDefaultSort(pageable, Sort.by("attendanceDate"));
        // Month range is ≤31 rows — load once (no COUNT query) then page in memory.
        List<Attendance> all = attendanceRepository
                .findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        userId, ym.atDay(1), ym.atEndOfMonth());
        return pageMonthHistory(all, effective);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> getCompanyAttendanceToday(Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        Pageable effective = applyDefaultSort(pageable, Sort.by("user.fullName"));
        Page<Attendance> page = attendanceRepository.findByCompanyIdAndAttendanceDate(
                companyId, AppTime.today(), effective);
        return toPageResponse(page, toResponsesFromStoredLate(page.getContent()));
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
        List<Attendance> all = attendanceRepository
                .findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        employeeId, ym.atDay(1), ym.atEndOfMonth());
        return pageMonthHistory(all, effective);
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
        ensureSameCompany(user, companyId);

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
     * List/history mapping: punches batched once; {@code isLate} from stored column
     * (no shift resolve on the read path).
     */
    private List<AttendanceResponse> toResponsesFromStoredLate(List<Attendance> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttendancePunch>> punchesByAttendance = loadPunchesGrouped(rows);
        List<AttendanceResponse> out = new ArrayList<>(rows.size());
        for (Attendance attendance : rows) {
            List<AttendancePunch> punches = punchesByAttendance.getOrDefault(
                    attendance.getId(), List.of());
            out.add(toResponse(attendance, punches, true));
        }
        return out;
    }

    private PageResponse<AttendanceResponse> pageMonthHistory(List<Attendance> all, Pageable pageable) {
        List<Attendance> sorted = sortAttendances(all, pageable.getSort());
        int total = sorted.size();
        int pageSize = Math.max(pageable.getPageSize(), 1);
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int from = pageNumber * pageSize;
        List<Attendance> slice = from >= total
                ? List.of()
                : sorted.subList(from, Math.min(from + pageSize, total));
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        List<AttendanceResponse> content = toResponsesFromStoredLate(slice);
        return PageResponse.<AttendanceResponse>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(total)
                .totalPages(totalPages)
                .isFirst(pageNumber == 0)
                .isLast(totalPages == 0 || pageNumber >= totalPages - 1)
                .hasNext(pageNumber + 1 < totalPages)
                .hasPrevious(pageNumber > 0)
                .build();
    }

    private List<Attendance> sortAttendances(List<Attendance> rows, Sort sort) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Sort.Order order = sort != null && sort.isSorted()
                ? sort.iterator().next()
                : Sort.Order.asc("attendanceDate");
        Comparator<Attendance> cmp;
        String prop = order.getProperty();
        if ("user.fullName".equals(prop)) {
            cmp = Comparator.comparing(
                    a -> a.getUser() != null && a.getUser().getFullName() != null
                            ? a.getUser().getFullName() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else {
            cmp = Comparator.comparing(Attendance::getAttendanceDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (order.isDescending()) {
            cmp = cmp.reversed();
        }
        List<Attendance> copy = new ArrayList<>(rows);
        copy.sort(cmp);
        return copy;
    }

    private Map<Long, List<AttendancePunch>> loadPunchesGrouped(List<Attendance> rows) {
        List<Long> ids = rows.stream()
                .map(Attendance::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return attendancePunchRepository.findByAttendanceIdInOrderByPunchTimeAsc(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getAttendance().getId()));
    }

    private PageResponse<AttendanceResponse> toPageResponse(
            Page<Attendance> page, List<AttendanceResponse> content) {
        return PageResponse.<AttendanceResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
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
