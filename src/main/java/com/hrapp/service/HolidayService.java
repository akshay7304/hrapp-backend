package com.hrapp.service;

import com.hrapp.util.AppTime;

import com.hrapp.dto.request.BulkHolidayRequest;
import com.hrapp.dto.request.HolidayRequest;
import com.hrapp.dto.request.ImportHolidayPresetsRequest;
import com.hrapp.dto.response.BulkHolidayImportResponse;
import com.hrapp.dto.response.BulkHolidayItemResult;
import com.hrapp.dto.response.HolidayPresetResponse;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Holiday;
import com.hrapp.enums.BulkHolidayStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.security.SecurityUtil;
import com.hrapp.util.IndianHolidayCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holiday CRUD. SUPERADMIN can manage any company; ADMIN is locked to their
 * own company regardless of the path {@code companyId}. Reads are open to any
 * authenticated user but similarly scoped for non-SUPERADMIN callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HolidayService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final int HOLIDAY_NAME_MAX = 100;

    private final HolidayRepository holidayRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public HolidayResponse createHoliday(Long companyId, HolidayRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        if (holidayRepository.existsByCompanyIdAndHolidayDate(effectiveCompanyId, request.getHolidayDate())) {
            throw new ConflictException("A holiday already exists on this date");
        }

        Company company = companyRepository.findById(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Holiday holiday = Holiday.builder()
                .company(company)
                .name(request.getName())
                .holidayDate(request.getHolidayDate())
                .build();
        holiday = holidayRepository.save(holiday);

        log.info("Created holiday id={} date={} in company={}",
                holiday.getId(), holiday.getHolidayDate(), effectiveCompanyId);
        return toResponse(holiday);
    }

    /**
     * Presets for the multi-select dialog. {@code alreadyAdded} is true when
     * this company already has a holiday on that date.
     */
    @Transactional(readOnly = true)
    public List<HolidayPresetResponse> getPresets(Long companyId, Integer year) {
        int effectiveYear = year != null ? year : AppTime.today().getYear();
        requireSupportedYear(effectiveYear);
        Long effectiveCompanyId = resolveCompanyId(companyId);

        Set<LocalDate> taken = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByCompanyIdAndHolidayDateBetween(
                effectiveCompanyId,
                LocalDate.of(effectiveYear, 1, 1),
                LocalDate.of(effectiveYear, 12, 31))) {
            taken.add(holiday.getHolidayDate());
        }

        return IndianHolidayCatalog.forYear(effectiveYear).stream()
                .map(preset -> HolidayPresetResponse.builder()
                        .code(preset.code())
                        .name(preset.name())
                        .holidayDate(preset.holidayDate())
                        .alreadyAdded(taken.contains(preset.holidayDate()))
                        .build())
                .toList();
    }

    /**
     * Imports the presets the admin ticked. Unknown codes are Failed rows.
     * A date that is already a holiday is skipped and counted as Already Exists.
     */
    @Transactional
    public BulkHolidayImportResponse importPresets(Long companyId, ImportHolidayPresetsRequest request) {
        requireSupportedYear(request.getYear());

        List<HolidayDraft> drafts = new ArrayList<>();
        for (String rawCode : request.getCodes()) {
            if (rawCode == null || rawCode.isBlank()) {
                drafts.add(HolidayDraft.invalid(null, null, "Holiday code is required"));
                continue;
            }
            String code = rawCode.trim();
            IndianHolidayCatalog.find(request.getYear(), code).ifPresentOrElse(
                    preset -> drafts.add(HolidayDraft.valid(preset.name(), preset.holidayDate())),
                    () -> drafts.add(HolidayDraft.invalid(code, null, "Unknown holiday preset: " + code)));
        }
        return saveAll(companyId, drafts);
    }

    /**
     * Creates many holidays in one call. One bad or duplicate row does not
     * roll back the ones that were added.
     */
    @Transactional
    public BulkHolidayImportResponse createHolidays(Long companyId, BulkHolidayRequest request) {
        List<HolidayDraft> drafts = new ArrayList<>();
        for (HolidayRequest item : request.getHolidays()) {
            if (item == null) {
                drafts.add(HolidayDraft.invalid(null, null, "Holiday is required"));
                continue;
            }
            drafts.add(HolidayDraft.valid(item.getName(), item.getHolidayDate()));
        }
        return saveAll(companyId, drafts);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> getHolidays(Long companyId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        return holidayRepository.findByCompanyIdOrderByHolidayDateAsc(effectiveCompanyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HolidayResponse updateHoliday(Long companyId, Long id, HolidayRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        if (!holiday.getHolidayDate().equals(request.getHolidayDate())
                && holidayRepository.existsByCompanyIdAndHolidayDate(effectiveCompanyId, request.getHolidayDate())) {
            throw new ConflictException("A holiday already exists on this date");
        }

        holiday.setName(request.getName());
        holiday.setHolidayDate(request.getHolidayDate());
        holiday = holidayRepository.save(holiday);

        log.info("Updated holiday id={} date={}", holiday.getId(), holiday.getHolidayDate());
        return toResponse(holiday);
    }

    @Transactional
    public void deleteHoliday(Long companyId, Long id) {
        Long effectiveCompanyId = resolveCompanyId(companyId);

        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        holidayRepository.delete(holiday);
        log.info("Deleted holiday id={}", id);
    }

    /**
     * SUPERADMIN can target any company by id; everyone else is locked to
     * their own company regardless of what they pass in the path.
     */
    private Long resolveCompanyId(Long pathCompanyId) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            if (pathCompanyId == null) {
                throw new BadRequestException("companyId is required");
            }
            companyRepository.findById(pathCompanyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
            return pathCompanyId;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId == null) {
            throw new UnauthorizedException("Caller is not bound to a company");
        }
        if (pathCompanyId != null && !pathCompanyId.equals(callerCompanyId)) {
            throw new UnauthorizedException("Cannot access another company's data");
        }
        return callerCompanyId;
    }

    private BulkHolidayImportResponse saveAll(Long companyId, List<HolidayDraft> drafts) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        Company company = companyRepository.findById(effectiveCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Set<LocalDate> taken = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByCompanyId(effectiveCompanyId)) {
            taken.add(holiday.getHolidayDate());
        }

        List<BulkHolidayItemResult> results = new ArrayList<>();
        int added = 0;
        int alreadyExists = 0;
        int failed = 0;

        for (HolidayDraft draft : drafts) {
            if (draft.failureReason != null) {
                failed++;
                results.add(result(draft.name, draft.holidayDate, BulkHolidayStatus.FAILED, null, draft.failureReason));
                continue;
            }

            String name = draft.name == null ? "" : draft.name.trim();
            if (name.isEmpty()) {
                failed++;
                results.add(result(draft.name, draft.holidayDate, BulkHolidayStatus.FAILED, null, "Holiday name is required"));
                continue;
            }
            if (name.length() > HOLIDAY_NAME_MAX) {
                failed++;
                results.add(result(name, draft.holidayDate, BulkHolidayStatus.FAILED, null,
                        "Holiday name must be at most " + HOLIDAY_NAME_MAX + " characters"));
                continue;
            }
            if (draft.holidayDate == null) {
                failed++;
                results.add(result(name, null, BulkHolidayStatus.FAILED, null, "Holiday date is required"));
                continue;
            }
            if (!taken.add(draft.holidayDate)) {
                alreadyExists++;
                results.add(result(name, draft.holidayDate, BulkHolidayStatus.ALREADY_EXISTS, null,
                        "A holiday already exists on this date"));
                continue;
            }

            Holiday holiday = holidayRepository.save(Holiday.builder()
                    .company(company)
                    .name(name)
                    .holidayDate(draft.holidayDate)
                    .build());
            added++;
            results.add(result(holiday.getName(), holiday.getHolidayDate(), BulkHolidayStatus.ADDED, holiday.getId(), null));
        }

        String summary = added + " Added, " + alreadyExists + " Already Exists, " + failed + " Failed";
        log.info("Bulk holidays company={} {}", effectiveCompanyId, summary);
        return BulkHolidayImportResponse.builder()
                .added(added)
                .alreadyExists(alreadyExists)
                .failed(failed)
                .summary(summary)
                .results(results)
                .build();
    }

    private void requireSupportedYear(int year) {
        if (!IndianHolidayCatalog.supports(year)) {
            throw new BadRequestException(
                    "Preset holidays are available for " + IndianHolidayCatalog.supportedYearLabel());
        }
    }

    private BulkHolidayItemResult result(String name, LocalDate holidayDate, BulkHolidayStatus status,
                                         Long id, String reason) {
        return BulkHolidayItemResult.builder()
                .name(name)
                .holidayDate(holidayDate)
                .status(status)
                .id(id)
                .reason(reason)
                .build();
    }

    private HolidayResponse toResponse(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .companyId(holiday.getCompany() != null ? holiday.getCompany().getId() : null)
                .createdAt(holiday.getCreatedAt())
                .build();
    }

    private static final class HolidayDraft {
        private final String name;
        private final LocalDate holidayDate;
        private final String failureReason;

        private HolidayDraft(String name, LocalDate holidayDate, String failureReason) {
            this.name = name;
            this.holidayDate = holidayDate;
            this.failureReason = failureReason;
        }

        private static HolidayDraft valid(String name, LocalDate holidayDate) {
            return new HolidayDraft(name, holidayDate, null);
        }

        private static HolidayDraft invalid(String name, LocalDate holidayDate, String reason) {
            return new HolidayDraft(name, holidayDate, reason);
        }
    }
}
