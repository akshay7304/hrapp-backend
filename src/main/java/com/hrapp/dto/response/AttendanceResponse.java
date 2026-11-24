package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String empCode;
    private LocalDate attendanceDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    /** Exact duration {@code HH:MM:SS}, taken from punch timestamps. */
    private String workedHours;
    /** Exact overtime {@code HH:MM:SS}. */
    private String overtimeHours;
    private String status;
    private String source;
    private Boolean isManual;
    private Boolean isAutoCheckout;
    private String manualReason;
    private List<PunchResponse> punches;
    private Integer totalSessions;
    private Boolean isCheckedIn;
}
