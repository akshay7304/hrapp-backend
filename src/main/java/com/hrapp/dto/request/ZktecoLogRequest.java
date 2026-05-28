package com.hrapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Push payload from a ZKTeco biometric device. The {@code type} field is the
 * device-reported direction:
 * <ul>
 *   <li>{@code 0} → IN</li>
 *   <li>{@code 1} → OUT</li>
 *   <li>{@code null} → server auto-detects from the day's punch parity</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZktecoLogRequest {

    @NotBlank(message = "Device serial number (sn) is required")
    private String sn;

    @NotBlank(message = "Device secret is required")
    private String deviceSecret;

    @NotBlank(message = "User enrol number is required")
    private String userEnrollNumber;

    /** Wall-clock punch time in {@code yyyy-MM-dd HH:mm:ss} format. */
    @NotBlank(message = "Record time is required")
    private String recordTime;

    /** {@code 0}=IN, {@code 1}=OUT, {@code null}=auto-detect. */
    private Integer type;
}
