package com.hrapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Generic punch payload for any biometric device that doesn't match the
 * ZKTeco / eSSL endpoints. The {@code punchType} field is the device-reported
 * direction:
 * <ul>
 *   <li>{@code "IN"} → IN</li>
 *   <li>{@code "OUT"} → OUT</li>
 *   <li>{@code null} → server auto-detects from the day's punch parity</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenericLogRequest {

    @NotBlank(message = "Device id is required")
    private String deviceId;

    @NotBlank(message = "Device secret is required")
    private String deviceSecret;

    @NotBlank(message = "Employee id is required")
    private String employeeId;

    /** Wall-clock punch time in ISO-8601 format. */
    @NotBlank(message = "Punch time is required")
    private String punchTime;

    /** {@code "IN"}, {@code "OUT"}, or {@code null} for auto-detect. */
    private String punchType;
}
