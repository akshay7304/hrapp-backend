package com.hrapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Mobile is required")
    @Pattern(regexp = "[0-9]{10}", message = "Mobile must be exactly 10 digits")
    private String mobile;

    @NotBlank(message = "Password is required")
    private String password;
}
