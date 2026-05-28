package com.hrapp.service;

import com.hrapp.dto.request.EsslLogRequest;
import com.hrapp.dto.request.GenericLogRequest;
import com.hrapp.dto.request.ZktecoLogRequest;
import com.hrapp.dto.response.ThumbLogResponse;
import com.hrapp.dto.response.ThumbProcessResult;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.Company;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.ThumbLog;
import com.hrapp.entity.User;
import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.PunchType;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.ThumbLogRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Biometric / thumb-scanner integration.
 * <p>
 * Devices push individual punches to {@code /thumb/zkteco}, {@code /thumb/essl}
 * or {@code /thumb/generic}; each call is authenticated by a per-company
 * shared secret stored on {@link CompanySettings}. Punches land in
 * {@code thumb_logs} unprocessed, and a periodic job (or the manual
 * {@code /thumb/process} trigger) compacts them into {@code attendance} rows.
 *
 * <p>The two layers are deliberately split so a device with a flaky link can
 * batch-push offline punches without racing the attendance summariser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbScannerService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private static final DateTimeFormatter ZKTECO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter ISO_MILLIS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ThumbLogRepository thumbLogRepository;
    private final UserRepository userRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final AttendanceRepository attendanceRepository;
    @SuppressWarnings("unused") // wired per spec for future holiday-aware overrides
    private final com.hrapp.repository.HolidayRepository holidayRepository;

    // ============================================================
    //  Device push entry points
    // ============================================================

    @Transactional
    public ThumbLogResponse processZktecoLog(ZktecoLogRequest request) {
        CompanySettings settings = findCompanyByDeviceSerial(
                request.getSn(), request.getDeviceSecret());

        LocalDateTime punchTime = parseDateTime(request.getRecordTime());
        Long companyId = settings.getCompany().getId();
        String deviceEmpId = request.getUserEnrollNumber();

        PunchType punchType = resolvePunchType(
                request.getType() == null ? null : request.getType() == 0 ? PunchType.IN : PunchType.OUT,
                companyId, deviceEmpId, punchTime);

        return saveAndRespond(settings.getCompany(), deviceEmpId, punchTime, punchType);
    }

    @Transactional
    public ThumbLogResponse processEsslLog(EsslLogRequest request) {
        CompanySettings settings = findCompanyByDeviceSerial(
                request.getDeviceId(), request.getDeviceSecret());

        LocalDateTime punchTime = parseDateTime(request.getLogDate());
        Long companyId = settings.getCompany().getId();
        String deviceEmpId = request.getUserId();

        PunchType hint = null;
        if (request.getDirection() != null) {
            if ("in".equalsIgnoreCase(request.getDirection())) {
                hint = PunchType.IN;
            } else if ("out".equalsIgnoreCase(request.getDirection())) {
                hint = PunchType.OUT;
            }
        }
        PunchType punchType = resolvePunchType(hint, companyId, deviceEmpId, punchTime);

        return saveAndRespond(settings.getCompany(), deviceEmpId, punchTime, punchType);
    }

    @Transactional
    public ThumbLogResponse processGenericLog(GenericLogRequest request) {
        CompanySettings settings = findCompanyByDeviceSerial(
                request.getDeviceId(), request.getDeviceSecret());

        LocalDateTime punchTime = parseDateTime(request.getPunchTime());
        Long companyId = settings.getCompany().getId();
        String deviceEmpId = request.getEmployeeId();

        PunchType hint = null;
        if (request.getPunchType() != null) {
            if ("IN".equalsIgnoreCase(request.getPunchType())) {
                hint = PunchType.IN;
            } else if ("OUT".equalsIgnoreCase(request.getPunchType())) {
                hint = PunchType.OUT;
            }
        }
        PunchType punchType = resolvePunchType(hint, companyId, deviceEmpId, punchTime);

        return saveAndRespond(settings.getCompany(), deviceEmpId, punchTime, punchType);
    }

    // ============================================================
    //  Batch processor — thumb_logs → attendance
    // ============================================================

    /**
     * Drains the unprocessed punch queue for {@code companyId} into
     * {@code attendance} rows. Each call is atomic per (user, date) group:
     * if any single group blows up, the rest still get processed and the
     * failures are returned in {@link ThumbProcessResult#getErrors()}.
     */
    @Transactional
    public ThumbProcessResult processThumbLogs(Long companyId) {
        List<ThumbLog> pending = thumbLogRepository
                .findByCompanyIdAndIsProcessedFalseOrderBySyncedAtAsc(companyId);
        if (pending.isEmpty()) {
            return ThumbProcessResult.builder()
                    .totalProcessed(0)
                    .successCount(0)
                    .skippedCount(0)
                    .errorCount(0)
                    .errors(new ArrayList<>())
                    .build();
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        int total = pending.size();
        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        Map<String, List<ThumbLog>> byDeviceEmp = pending.stream()
                .collect(Collectors.groupingBy(ThumbLog::getDeviceEmpId));

        for (Map.Entry<String, List<ThumbLog>> empEntry : byDeviceEmp.entrySet()) {
            String deviceEmpId = empEntry.getKey();
            List<ThumbLog> empLogs = empEntry.getValue();

            Optional<User> userOpt = userRepository
                    .findByThumbDeviceIdAndCompanyId(deviceEmpId, companyId);
            if (userOpt.isEmpty()) {
                errorCount += empLogs.size();
                String msg = "No user mapped to device id '" + deviceEmpId
                        + "' in company " + companyId;
                log.warn(msg);
                errors.add(msg);
                continue;
            }
            User user = userOpt.get();
            Company company = user.getCompany();

            // TreeMap keeps date groups in chronological order — helpful when
            // the same payload spans an overnight backlog.
            Map<LocalDate, List<ThumbLog>> byDate = empLogs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getPunchTime().toLocalDate(),
                            TreeMap::new,
                            Collectors.toList()));

            for (Map.Entry<LocalDate, List<ThumbLog>> dateEntry : byDate.entrySet()) {
                LocalDate date = dateEntry.getKey();
                List<ThumbLog> dateLogs = dateEntry.getValue();
                dateLogs.sort(Comparator.comparing(ThumbLog::getPunchTime));

                ThumbLog firstIn = dateLogs.stream()
                        .filter(l -> l.getPunchType() == PunchType.IN)
                        .findFirst()
                        .orElse(null);
                ThumbLog lastOut = null;
                for (int i = dateLogs.size() - 1; i >= 0; i--) {
                    if (dateLogs.get(i).getPunchType() == PunchType.OUT) {
                        lastOut = dateLogs.get(i);
                        break;
                    }
                }

                if (firstIn == null) {
                    // OUT-only group → nothing to anchor an attendance row on.
                    skippedCount += dateLogs.size();
                    continue;
                }

                try {
                    upsertAttendance(user, company, date, firstIn, lastOut, settings);
                    dateLogs.forEach(l -> l.setIsProcessed(true));
                    thumbLogRepository.saveAll(dateLogs);
                    successCount += dateLogs.size();
                } catch (Exception ex) {
                    errorCount += dateLogs.size();
                    String msg = "Failed processing user=" + user.getId()
                            + " date=" + date + " — " + ex.getMessage();
                    log.error(msg, ex);
                    errors.add(msg);
                }
            }
        }

        log.info("Thumb processing for company={} total={} success={} skipped={} error={}",
                companyId, total, successCount, skippedCount, errorCount);
        return ThumbProcessResult.builder()
                .totalProcessed(total)
                .successCount(successCount)
                .skippedCount(skippedCount)
                .errorCount(errorCount)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ThumbLogResponse> getPendingLogs(Long requestedCompanyId) {
        Long effectiveCompanyId;
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            // SUPERADMIN may inspect any tenant; fall back to their own only if
            // no override was sent (they have no company themselves usually).
            effectiveCompanyId = requestedCompanyId != null
                    ? requestedCompanyId
                    : SecurityUtil.getCurrentUserCompanyId();
            if (effectiveCompanyId == null) {
                throw new BadRequestException("companyId is required for SUPERADMIN callers");
            }
        } else {
            Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
            if (callerCompanyId == null) {
                throw new UnauthorizedException("Caller is not bound to a company");
            }
            effectiveCompanyId = callerCompanyId;
        }
        return thumbLogRepository
                .findByCompanyIdAndIsProcessedFalseOrderBySyncedAtAsc(effectiveCompanyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================================================
    //  Internal helpers — device routing, parsing, persistence
    // ============================================================

    /**
     * Resolves a device-side serial to a {@link CompanySettings} record and
     * verifies the per-company shared secret in one step.
     * <p>
     * Routing works through the {@code users.thumb_device_id} index — any user
     * enrolled on the device tells us which company the device belongs to.
     * This is cheaper than scanning {@code company_settings} and avoids
     * needing a dedicated device-registry table; the typical deployment has
     * exactly one device per company so collisions across tenants don't
     * happen in practice.
     *
     * @throws BadRequestException if no user is enrolled with this serial,
     *         if the resolved company has no settings row, or if the
     *         supplied secret doesn't match.
     */
    private CompanySettings findCompanyByDeviceSerial(String deviceSerial, String deviceSecret) {
        if (deviceSerial == null || deviceSerial.isBlank()) {
            throw new BadRequestException("Unknown device");
        }

        User user = userRepository.findByThumbDeviceId(deviceSerial)
                .orElseThrow(() -> new BadRequestException("Unknown device"));

        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (companyId == null) {
            throw new BadRequestException("Unknown device");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        if (settings.getDeviceSecret() == null
                || !settings.getDeviceSecret().equals(deviceSecret)) {
            throw new BadRequestException("Invalid device secret");
        }
        return settings;
    }

    /**
     * Decides whether a punch is IN or OUT when the device didn't tell us.
     * Counts every punch (processed or not) recorded for this device employee
     * today and uses parity:
     * <ul>
     *   <li>even (0, 2, 4, …) → IN (starting a new session)</li>
     *   <li>odd (1, 3, 5, …) → OUT (closing a session)</li>
     * </ul>
     */
    private PunchType detectPunchType(Long companyId, String deviceEmpId, LocalDateTime punchTime) {
        LocalDate date = punchTime.toLocalDate();
        long count = thumbLogRepository.countByCompanyIdAndDeviceEmpIdAndPunchTimeBetween(
                companyId, deviceEmpId,
                date.atStartOfDay(),
                date.atTime(LocalTime.MAX));
        return count % 2 == 0 ? PunchType.IN : PunchType.OUT;
    }

    /** Returns the device-provided {@code hint} if non-null, otherwise auto-detects. */
    private PunchType resolvePunchType(PunchType hint, Long companyId,
                                       String deviceEmpId, LocalDateTime punchTime) {
        return hint != null ? hint : detectPunchType(companyId, deviceEmpId, punchTime);
    }

    /**
     * Tries each known device timestamp format in order. The order is
     * "most specific first" so a string with millis isn't accepted by the
     * looser ISO format and silently lose precision.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new BadRequestException("Invalid date format");
        }
        for (DateTimeFormatter fmt : List.of(ISO_MILLIS_FORMAT, ISO_FORMAT, ZKTECO_FORMAT)) {
            try {
                return LocalDateTime.parse(dateTimeStr, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new BadRequestException("Invalid date format");
    }

    private ThumbLogResponse saveAndRespond(Company company, String deviceEmpId,
                                            LocalDateTime punchTime, PunchType punchType) {
        ThumbLog saved = thumbLogRepository.save(ThumbLog.builder()
                .company(company)
                .deviceEmpId(deviceEmpId)
                .punchTime(punchTime)
                .punchType(punchType)
                .isProcessed(false)
                .build());
        return toResponse(saved);
    }

    /**
     * Merges a single (user, date) group of punches into an attendance row.
     * Existing rows keep the earliest {@code checkIn} and latest {@code checkOut}
     * so re-running the processor on the same data is idempotent.
     */
    private void upsertAttendance(User user, Company company, LocalDate date,
                                  ThumbLog firstIn, ThumbLog lastOut,
                                  CompanySettings settings) {
        Attendance attendance = attendanceRepository
                .findByUserIdAndAttendanceDate(user.getId(), date)
                .orElseGet(() -> Attendance.builder()
                        .user(user)
                        .company(company)
                        .attendanceDate(date)
                        .workedHours(BigDecimal.ZERO)
                        .overtimeHours(BigDecimal.ZERO)
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.THUMB)
                        .isManual(false)
                        .build());

        LocalDateTime newCheckIn = firstIn.getPunchTime();
        if (attendance.getCheckIn() == null || newCheckIn.isBefore(attendance.getCheckIn())) {
            attendance.setCheckIn(newCheckIn);
        }
        if (lastOut != null) {
            LocalDateTime newCheckOut = lastOut.getPunchTime();
            if (attendance.getCheckOut() == null || newCheckOut.isAfter(attendance.getCheckOut())) {
                attendance.setCheckOut(newCheckOut);
            }
        }

        if (attendance.getCheckOut() != null && attendance.getCheckIn() != null) {
            BigDecimal worked = calculateHours(attendance.getCheckIn(), attendance.getCheckOut());
            attendance.setWorkedHours(worked);
            attendance.setOvertimeHours(calculateOvertime(worked, settings.getOvertimeAfterHours()));
            attendance.setStatus(deriveStatus(worked, settings));
        }

        attendanceRepository.save(attendance);
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

    private ThumbLogResponse toResponse(ThumbLog log) {
        return ThumbLogResponse.builder()
                .id(log.getId())
                .companyId(log.getCompany() != null ? log.getCompany().getId() : null)
                .deviceEmpId(log.getDeviceEmpId())
                .punchTime(log.getPunchTime())
                .punchType(log.getPunchType() != null ? log.getPunchType().name() : null)
                .isProcessed(log.getIsProcessed())
                .syncedAt(log.getSyncedAt())
                .build();
    }
}
