package com.hrapp.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class LeaveTypeRequest {

    @NotBlank(message = "Leave type name is required")
    private String name;

    @NotNull(message = "Annual quota is required")
    @Min(value = 0, message = "Annual quota cannot be negative")
    private Integer annualQuota;

    @NotNull(message = "Paid/unpaid flag is required")
    private Boolean isPaid;
}
