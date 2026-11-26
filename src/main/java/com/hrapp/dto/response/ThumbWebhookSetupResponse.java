package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One-time thumb-device wiring package returned on company create only.
 * Plaintext {@code deviceSecret} is never echoed on subsequent GETs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbWebhookSetupResponse {

    private String deviceBrand;
    private String deviceSerial;
    private String deviceSecret;
    private String zktecoWebhookPath;
    private String esslWebhookPath;
    private String genericWebhookPath;
}
