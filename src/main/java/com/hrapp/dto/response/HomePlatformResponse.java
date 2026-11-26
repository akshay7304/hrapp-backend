package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * SUPERADMIN home payload — platform footprint and attention signals.
 * Filled only when {@link HomeResponse#getRole()} is SUPERADMIN.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomePlatformResponse {

    private Long totalCompanies;
    private Long activeCompanies;
    private Long inactiveCompanies;
    private Long companiesCreatedLast30Days;
    private Long totalEmployees;

    private Long companiesMissingDeviceSerial;
    private Long companiesWithPendingThumbLogs;
    private Long companiesWithoutAdmin;
    private Long companiesWithNoPayrollThisMonth;

    private Long pendingThumbLogsTotal;
    private Long pendingLeaveRequestsTotal;
    private Long pendingAdvancesTotal;

    private List<HomePlatformCompanyCardResponse> recentCompanies;
}
