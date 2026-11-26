package com.hrapp.repository;

/** Single-row platform KPI aggregate for SUPERADMIN home. */
public record PlatformStatsRow(
        long totalCompanies,
        long activeCompanies,
        long inactiveCompanies,
        long companiesCreatedLast30Days,
        long totalEmployees,
        long companiesMissingDeviceSerial,
        long companiesWithPendingThumbLogs,
        long companiesWithoutAdmin,
        long companiesWithNoPayrollThisMonth,
        long pendingThumbLogsTotal,
        long pendingLeaveRequestsTotal,
        long pendingAdvancesTotal
) {
}
