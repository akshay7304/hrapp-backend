package com.hrapp.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Placeholder body for {@code POST /attendance/check-in}.
 * <p>
 * Currently empty — user identity is derived from the JWT — but kept as a
 * dedicated DTO so future fields (e.g. geolocation, device id) can be added
 * without breaking the controller signature.
 */
@Getter
@Setter
@NoArgsConstructor
public class CheckInRequest {
}
