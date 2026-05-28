package com.hrapp.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class MarkPaidRequest {

    @NotNull(message = "Paid on date is required")
    private LocalDate paidOn;

    private String remarks;
}
