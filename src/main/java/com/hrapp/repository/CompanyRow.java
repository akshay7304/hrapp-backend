package com.hrapp.repository;

import java.time.LocalDateTime;

/** Company list row with employee count aggregated in SQL. */
public record CompanyRow(
        Long id,
        String name,
        String address,
        String logoUrl,
        Boolean isActive,
        Integer totalEmployees,
        LocalDateTime createdAt
) {
}
