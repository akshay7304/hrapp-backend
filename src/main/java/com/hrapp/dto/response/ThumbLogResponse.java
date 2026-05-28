package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbLogResponse {

    private Long id;
    private Long companyId;
    private String deviceEmpId;
    private LocalDateTime punchTime;
    private String punchType;
    private Boolean isProcessed;
    private LocalDateTime syncedAt;
}
