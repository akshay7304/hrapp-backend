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
public class AdvanceResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String empCode;
    private BigDecimal amount;
    /** Requester's original reason (never overwritten on reject/cancel). */
    private String reason;
    /** Present when rejected or cancelled by employee. */
    private String rejectReason;
    private String status;
    private Integer deductFromMonth;
    private Integer deductFromYear;
    private Boolean isRecovered;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime actionedAt;
}
