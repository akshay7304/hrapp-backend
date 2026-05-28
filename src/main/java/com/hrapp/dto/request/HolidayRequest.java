package com.hrapp.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class HolidayRequest {

    @NotBlank(message = "Holiday name is required")
    private String name;

    @NotNull(message = "Holiday date is required")
    private LocalDate holidayDate;
}
