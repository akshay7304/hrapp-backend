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
public class ConfirmRotationDraftRequest {

    /**
     * Date from which proposed shifts become live assignments.
     * Defaults to the draft period start when omitted from callers that
     * still send the field — controller requires it for clarity.
     */
    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;
}
