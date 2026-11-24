package com.hrapp.dto.request;

import com.hrapp.enums.RotationPeriodUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRotationRequest {

    @NotBlank(message = "name is required")
    @Size(max = 80)
    private String name;

    @NotNull(message = "periodUnit is required")
    private RotationPeriodUnit periodUnit;

    /** Weeks when WEEKLY; calendar days when DAYS. Must be >= 1. */
    @NotNull(message = "periodLength is required")
    private Integer periodLength;

    /** Ordered cycle of company shift ids (min 2). */
    @NotEmpty(message = "shiftIds are required")
    private List<Long> shiftIds;
}
