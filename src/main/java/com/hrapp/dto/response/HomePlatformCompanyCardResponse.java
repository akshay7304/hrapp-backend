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
public class HomePlatformCompanyCardResponse {

    private Long id;
    private String name;
    private Boolean isActive;
    private Integer totalEmployees;
    private String adminName;
    private String adminMobile;
    private LocalDateTime createdAt;
    private Boolean hasSettings;
    private Boolean hasDeviceSerial;
    private Boolean hasAdmin;
    private Long pendingThumbLogs;
    private Boolean payrollRunThisMonth;
}
