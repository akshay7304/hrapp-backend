package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettingsResponse {

    private Long id;
    private Long companyId;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private BigDecimal shiftHours;
    private BigDecimal halfDayHours;
    private BigDecimal overtimeAfterHours;
    private Integer lateMarkAfterMinutes;
    private String weekOffDay;
    private LocalDateTime updatedAt;
}
