package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single home-screen payload. {@code me} is always filled for company users;
 * {@code ops} is filled only for ADMIN/HR.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeResponse {

    /** Highest role from the access token (SUPERADMIN / ADMIN / HR / EMPLOYEE). */
    private String role;
    private HomeMeResponse me;
    private HomeOpsResponse ops;
}
