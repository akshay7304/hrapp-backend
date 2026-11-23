package com.hrapp.dto.request;

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
public class ImportHolidayPresetsRequest {

    @NotNull(message = "Year is required")
    private Integer year;

    /** Preset codes from {@code GET /holidays/company/{companyId}/presets}, e.g. {@code DIWALI}. */
    @NotEmpty(message = "Select at least one holiday")
    @Size(max = 50, message = "Cannot import more than 50 presets at once")
    private List<String> codes;
}
