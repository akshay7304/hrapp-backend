package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.request.DesignationRequest;
import com.hrapp.dto.response.DesignationResponse;
import com.hrapp.service.DesignationService;
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
@RequestMapping("/designations")
@RequiredArgsConstructor
@Tag(name = "05. Designations")
public class DesignationController {

    private final DesignationService designationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DesignationResponse>> createDesignation(
            @Valid @RequestBody DesignationRequest request) {
        DesignationResponse response = designationService.createDesignation(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Designation created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<DesignationResponse>>> getAllDesignations() {
        List<DesignationResponse> designations = designationService.getAllDesignations();
        return ResponseEntity.ok(ApiResponse.success(designations, "Designations retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DesignationResponse>> updateDesignation(
            @PathVariable Long id,
            @Valid @RequestBody DesignationRequest request) {
        DesignationResponse response = designationService.updateDesignation(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Designation updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDesignation(@PathVariable Long id) {
        designationService.deleteDesignation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Designation deleted successfully"));
    }
}
