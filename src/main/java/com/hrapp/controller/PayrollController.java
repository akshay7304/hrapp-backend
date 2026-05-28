package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.MarkPaidRequest;
import com.hrapp.dto.request.RunPayrollRequest;
import com.hrapp.dto.request.SalaryAdjustmentRequest;
import com.hrapp.dto.request.SalaryStructureRequest;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryAdjustmentResponse;
import com.hrapp.dto.response.SalaryPaymentResponse;
import com.hrapp.dto.response.SalaryStructureResponse;
import com.hrapp.service.PayrollService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/payroll")
@RequiredArgsConstructor
@Tag(name = "11. Payroll")
public class PayrollController {

    private final PayrollService payrollService;

    // ---------------- Salary Structure ----------------

    @PostMapping("/salary-structure")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SalaryStructureResponse>> setSalaryStructure(
            @Valid @RequestBody SalaryStructureRequest request) {
        SalaryStructureResponse data = payrollService.setSalaryStructure(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Salary structure saved successfully"));
    }

    @GetMapping("/salary-structure/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<SalaryStructureResponse>>> getSalaryStructure(
            @PathVariable("userId") Long userId) {
        List<SalaryStructureResponse> data = payrollService.getSalaryStructure(userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Salary structures fetched successfully"));
    }

    // ---------------- Adjustments ----------------

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SalaryAdjustmentResponse>> addAdjustment(
            @Valid @RequestBody SalaryAdjustmentRequest request) {
        SalaryAdjustmentResponse data = payrollService.addAdjustment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Adjustment created successfully"));
    }

    @GetMapping("/adjustments/{userId}/{month}/{year}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<SalaryAdjustmentResponse>>> getAdjustments(
            @PathVariable("userId") Long userId,
            @PathVariable("month") Integer month,
            @PathVariable("year") Integer year) {
        List<SalaryAdjustmentResponse> data = payrollService.getAdjustments(userId, month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Adjustments fetched successfully"));
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAdjustment(@PathVariable("id") Long id) {
        payrollService.deleteAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Adjustment deleted successfully"));
    }

    // ---------------- Payroll ----------------

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalaryPaymentResponse>>> runPayroll(
            @Valid @RequestBody RunPayrollRequest request) {
        List<SalaryPaymentResponse> data = payrollService.runPayroll(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Payroll run completed successfully"));
    }

    @GetMapping("/{month}/{year}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<PageResponse<SalaryPaymentResponse>>> getPayroll(
            @PathVariable("month") Integer month,
            @PathVariable("year") Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<SalaryPaymentResponse> data = payrollService.getPayroll(month, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Payroll fetched successfully"));
    }

    @GetMapping("/employee/{id}/{month}/{year}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<SalaryPaymentResponse>> getEmployeePayslip(
            @PathVariable("id") Long id,
            @PathVariable("month") Integer month,
            @PathVariable("year") Integer year) {
        SalaryPaymentResponse data = payrollService.getEmployeePayslip(id, month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Payslip fetched successfully"));
    }

    @PutMapping("/{id}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SalaryPaymentResponse>> markAsPaid(
            @PathVariable("id") Long id,
            @Valid @RequestBody MarkPaidRequest request) {
        SalaryPaymentResponse data = payrollService.markAsPaid(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Payslip marked as paid successfully"));
    }

    @GetMapping("/my/{month}/{year}")
    public ResponseEntity<ApiResponse<SalaryPaymentResponse>> getMyPayslip(
            @PathVariable("month") Integer month,
            @PathVariable("year") Integer year) {
        SalaryPaymentResponse data = payrollService.getMyPayslip(month, year);
        return ResponseEntity.ok(ApiResponse.success(data, "Payslip fetched successfully"));
    }
}
