package com.hrapp.service;

import com.hrapp.dto.response.RoleResponse;
import com.hrapp.entity.Role;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.RoleRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Roles a caller may assign on employee create/update.
 * SUPERADMIN is platform-only and is never offered.
 * A caller can assign their own role and anything below it: ADMIN may assign
 * ADMIN, HR, and EMPLOYEE; HR may assign EMPLOYEE only.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    static final String ROLE_SUPERADMIN = "SUPERADMIN";
    static final String ROLE_ADMIN = "ADMIN";
    static final String ROLE_HR = "HR";
    static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private static final List<String> ADMIN_ASSIGNABLE = List.of(ROLE_ADMIN, ROLE_HR, ROLE_EMPLOYEE);
    private static final List<String> HR_ASSIGNABLE = List.of(ROLE_EMPLOYEE);

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> getAssignableRoles() {
        List<String> allowed = namesForCaller();
        Map<String, Role> byName = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));

        List<RoleResponse> roles = new ArrayList<>();
        for (String name : allowed) {
            Role role = byName.get(name);
            if (role != null) {
                roles.add(toResponse(role));
            }
        }
        return roles;
    }

    public void ensureAssignable(Role role) {
        if (role == null || role.getName() == null) {
            throw new BadRequestException("Role is required");
        }
        List<String> allowed = namesForCaller();
        if (allowed.contains(role.getName())) {
            return;
        }
        if (ROLE_SUPERADMIN.equals(role.getName())) {
            throw new BadRequestException("Cannot assign SUPERADMIN");
        }
        throw new BadRequestException(
                "Cannot assign " + role.getName() + ". Allowed roles: " + String.join(", ", allowed));
    }

    private List<String> namesForCaller() {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN) || SecurityUtil.hasRole(ROLE_ADMIN)) {
            return ADMIN_ASSIGNABLE;
        }
        if (SecurityUtil.hasRole(ROLE_HR)) {
            return HR_ASSIGNABLE;
        }
        throw new UnauthorizedException("Not allowed to assign roles");
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .build();
    }
}
