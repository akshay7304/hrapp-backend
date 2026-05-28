package com.hrapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Push payload from an eSSL biometric device. The {@code direction} field is
 * the device-reported direction:
 * <ul>
 *   <li>{@code "in"} → IN</li>
 *   <li>{@code "out"} → OUT</li>
 *   <li>{@code null} → server auto-detects from the day's punch parity</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsslLogRequest {

    @NotBlank(message = "Device id is required")
    private String deviceId;

    @NotBlank(message = "Device secret is required")
    private String deviceSecret;

    @NotBlank(message = "User id is required")
    private String userId;

    /** Wall-clock punch time in ISO {@code yyyy-MM-dd'T'HH:mm:ss} format. */
    @NotBlank(message = "Log date is required")
    private String logDate;

    /** {@code "in"}, {@code "out"}, or {@code null} for auto-detect. */
    private String direction;
}
