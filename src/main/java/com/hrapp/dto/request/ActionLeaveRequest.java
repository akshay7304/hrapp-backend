package com.hrapp.dto.request;

import com.hrapp.enums.LeaveRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLeaveRequest {

    /** Must be either {@code APPROVED} or {@code REJECTED} — validated in the service. */
    @NotNull(message = "Status is required")
    private LeaveRequestStatus status;

    /** Required when {@code status = REJECTED}. */
    private String rejectReason;
}
