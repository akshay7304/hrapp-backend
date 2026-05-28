package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.HolidayRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
@Tag(name = "07. Holidays")
public class HolidayController {

    private final HolidayService holidayService;

    @PostMapping("/company/{companyId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(
            @PathVariable Long companyId,
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse response = holidayService.createHoliday(companyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Holiday created successfully"));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getHolidays(
            @PathVariable Long companyId) {
        List<HolidayResponse> holidays = holidayService.getHolidays(companyId);
        return ResponseEntity.ok(ApiResponse.success(holidays, "Holidays retrieved successfully"));
    }

    @PutMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse response = holidayService.updateHoliday(companyId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Holiday updated successfully"));
    }

    @DeleteMapping("/company/{companyId}/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        holidayService.deleteHoliday(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Holiday deleted successfully"));
    }
}
