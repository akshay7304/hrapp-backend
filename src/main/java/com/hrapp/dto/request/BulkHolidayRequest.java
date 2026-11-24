package com.hrapp.dto.request;

import jakarta.validation.constraints.NotEmpty;
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
public class BulkHolidayRequest {

    /**
     * Each item is checked on its own. A bad row is reported as Failed and
     * does not reject the rest of the batch.
     */
    @NotEmpty(message = "Select at least one holiday")
    @Size(max = 100, message = "Cannot import more than 100 holidays at once")
    private List<HolidayRequest> holidays;
}
