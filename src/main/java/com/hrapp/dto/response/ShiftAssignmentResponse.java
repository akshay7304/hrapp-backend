package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignmentResponse {

    private Long id;
    private Long companyId;
    private Long userId;
    private String fullName;
    private String empCode;
    private Long shiftId;
    private String shiftName;
    private LocalDate effectiveFrom;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
}
