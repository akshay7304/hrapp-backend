package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single home-screen payload.
 * <ul>
 *   <li>EMPLOYEE — {@code me}</li>
 *   <li>ADMIN/HR — {@code me} + {@code ops}</li>
 *   <li>SUPERADMIN (no company) — {@code platform} only</li>
 * </ul>
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
    private HomePlatformResponse platform;
}
