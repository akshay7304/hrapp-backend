package com.hrapp.dto.response;

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
public class BulkHolidayImportResponse {

    private int added;
    private int alreadyExists;
    private int failed;
    /** Example: {@code 12 Added, 1 Already Exists, 0 Failed}. */
    private String summary;
    private List<BulkHolidayItemResult> results;
}
