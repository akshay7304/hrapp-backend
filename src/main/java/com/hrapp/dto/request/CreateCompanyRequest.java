package com.hrapp.dto.request;

import jakarta.validation.constraints.Email;
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
public class CreateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String address;

    @NotBlank(message = "Admin full name is required")
    private String adminFullName;

    @NotBlank(message = "Admin mobile is required")
    @Pattern(regexp = "[0-9]{10}", message = "Admin mobile must be a 10-digit number")
    private String adminMobile;

    @Email(message = "Admin email must be a valid email address")
    private String adminEmail;
}
