package com.hrapp.dto.response;

import com.hrapp.enums.BulkHolidayStatus;
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
public class BulkHolidayItemResult {

    private String name;
    private LocalDate holidayDate;
    private BulkHolidayStatus status;
    private Long id;
    private String reason;
}
