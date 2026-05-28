package com.hrapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyResponse {

    private Long id;
    private String name;
    private String address;
    private String logoUrl;
    private Boolean isActive;
    private Integer totalEmployees;
    private LocalDateTime createdAt;
}
