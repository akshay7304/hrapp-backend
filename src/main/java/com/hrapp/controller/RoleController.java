package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.response.RoleResponse;
import com.hrapp.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "03b. Roles")
public class RoleController {

    private final RoleService roleService;

    /**
     * Dropdown for employee create/update. SUPERADMIN is never returned.
     * ADMIN receives ADMIN, HR, EMPLOYEE. HR receives EMPLOYEE only.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAssignableRoles() {
        List<RoleResponse> roles = roleService.getAssignableRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "Roles retrieved successfully"));
    }
}
