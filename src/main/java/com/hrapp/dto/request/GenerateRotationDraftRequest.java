package com.hrapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateRotationDraftRequest {

    /**
     * Optional period start. When omitted, starts the day after the last
     * confirmed period (or today). WEEKLY periods snap to Monday.
     */
    private LocalDate periodStart;
}
