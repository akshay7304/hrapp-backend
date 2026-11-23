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
public class ShiftResponse {

    private Long id;
    private Long companyId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOvernight;
    private Integer graceLateMinutes;
    private BigDecimal fullDayHours;
    private BigDecimal halfDayHours;
    private Integer breakMinutes;
    private Boolean breakPaid;
    private Boolean isDefault;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
