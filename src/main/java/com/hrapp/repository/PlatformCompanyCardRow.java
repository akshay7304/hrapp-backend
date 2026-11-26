package com.hrapp.repository;

import java.time.LocalDateTime;

/** Recent-company card row for SUPERADMIN home. */
public record PlatformCompanyCardRow(
        Long id,
        String name,
        Boolean isActive,
        Integer totalEmployees,
        String adminName,
        String adminMobile,
        LocalDateTime createdAt,
        Boolean hasSettings,
        Boolean hasDeviceSerial,
        Boolean hasAdmin,
        Long pendingThumbLogs,
        Boolean payrollRunThisMonth
) {
}
