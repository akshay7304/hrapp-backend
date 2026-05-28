package com.hrapp.dto.request;

import com.hrapp.enums.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualAttendanceRequest {

    @NotNull(message = "User id is required")
    private Long userId;

    @NotNull(message = "Attendance date is required")
    private LocalDate attendanceDate;

    private LocalDateTime checkIn;

    private LocalDateTime checkOut;

    @NotNull(message = "Status is required")
    private AttendanceStatus status;

    @NotBlank(message = "Manual reason is required")
    private String manualReason;
}
