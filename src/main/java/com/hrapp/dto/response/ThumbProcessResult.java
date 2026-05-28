package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Summary returned by the thumb-log processor — both the scheduler and the
 * manual {@code /thumb/process} endpoint use this to report what happened.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbProcessResult {

    private Integer totalProcessed;
    private Integer successCount;
    private Integer skippedCount;
    private Integer errorCount;
    private List<String> errors;
}
