package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    /** Lifetime of the access token, in milliseconds. */
    private Long accessTokenExpiresIn;

    private Long userId;

    /** Null for SUPERADMIN. */
    private Long companyId;

    private String fullName;

    /** Null for SUPERADMIN. */
    private String empCode;

    private List<String> roles;
}
