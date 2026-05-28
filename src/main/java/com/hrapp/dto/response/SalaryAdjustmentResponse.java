package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAdjustmentResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private Integer month;
    private Integer year;
    private String type;
    private BigDecimal amount;
    private String reason;
    private String createdByName;
    private LocalDateTime createdAt;
}
