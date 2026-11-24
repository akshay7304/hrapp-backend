package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.BulkHolidayRequest;
import com.hrapp.dto.request.HolidayRequest;
import com.hrapp.dto.request.ImportHolidayPresetsRequest;
import com.hrapp.dto.response.BulkHolidayImportResponse;
import com.hrapp.dto.response.HolidayPresetResponse;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.service.HolidayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
@Tag(name = "07. Holidays")
public class HolidayController {

    private final HolidayService holidayService;

    @PostMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(
            @PathVariable Long companyId,
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse response = holidayService.createHoliday(companyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Holiday created successfully"));
    }

    /**
     * Multi-select source for Indian gazetted holidays. Pass {@code year}
     * (defaults to the current year). {@code alreadyAdded} marks dates this
     * company already has.
     */
    @GetMapping("/company/{companyId}/presets")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<List<HolidayPresetResponse>>> getPresets(
            @PathVariable Long companyId,
            @RequestParam(required = false) Integer year) {
        List<HolidayPresetResponse> presets = holidayService.getPresets(companyId, year);
        return ResponseEntity.ok(ApiResponse.success(presets, "Holiday presets retrieved successfully"));
    }

    /**
     * Imports the presets selected in the dialog.
     * Message is the summary, e.g. {@code 12 Added, 1 Already Exists, 0 Failed}.
     */
    @PostMapping("/company/{companyId}/import")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<BulkHolidayImportResponse>> importPresets(
            @PathVariable Long companyId,
            @Valid @RequestBody ImportHolidayPresetsRequest request) {
        BulkHolidayImportResponse response = holidayService.importPresets(companyId, request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getSummary()));
    }

    /**
     * Creates many holidays at once. Duplicates and invalid rows are counted
     * and the rest are still saved.
     */
    @PostMapping("/company/{companyId}/bulk")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<BulkHolidayImportResponse>> createHolidays(
            @PathVariable Long companyId,
            @Valid @RequestBody BulkHolidayRequest request) {
        BulkHolidayImportResponse response = holidayService.createHolidays(companyId, request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getSummary()));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getHolidays(
            @PathVariable Long companyId) {
        List<HolidayResponse> holidays = holidayService.getHolidays(companyId);
        return ResponseEntity.ok(ApiResponse.success(holidays, "Holidays retrieved successfully"));
    }

    @PutMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse response = holidayService.updateHoliday(companyId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Holiday updated successfully"));
    }

    @DeleteMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        holidayService.deleteHoliday(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Holiday deleted successfully"));
    }
}
