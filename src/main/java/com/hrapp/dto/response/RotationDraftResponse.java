package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotationDraftResponse {

    private Long id;
    private Long rotationId;
    private String rotationName;
    private Long companyId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private LocalDateTime confirmedAt;
    private List<RotationDraftRowResponse> rows;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
