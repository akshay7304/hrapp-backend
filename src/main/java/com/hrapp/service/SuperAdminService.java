package com.hrapp.service;

import com.hrapp.dto.request.CreateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyStatusRequest;
import com.hrapp.dto.response.CompanyDetailResponse;
import com.hrapp.dto.response.CompanyResponse;
import com.hrapp.dto.response.HomePlatformCompanyCardResponse;
import com.hrapp.dto.response.HomePlatformResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.repository.PlatformCompanyCardRow;
import com.hrapp.repository.PlatformStatsRow;
import com.hrapp.repository.SuperAdminJdbcRepository;
import com.hrapp.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * SUPERADMIN platform home + thin company-management facade over
 * {@link CompanyService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private static final int RECENT_COMPANIES_LIMIT = 10;

    private final SuperAdminJdbcRepository superAdminJdbcRepository;
    private final CompanyService companyService;

    @Transactional(readOnly = true)
    public HomePlatformResponse getPlatformHome() {
        LocalDate today = AppTime.today();
        YearMonth ym = YearMonth.from(today);
        PlatformStatsRow stats = superAdminJdbcRepository.getPlatformStats(
                AppTime.now().minusDays(30), ym.getMonthValue(), ym.getYear());
        List<HomePlatformCompanyCardResponse> recent = superAdminJdbcRepository
                .getRecentCompanies(RECENT_COMPANIES_LIMIT, ym.getMonthValue(), ym.getYear())
                .stream()
                .map(this::toCard)
                .toList();

        return HomePlatformResponse.builder()
                .totalCompanies(stats.totalCompanies())
                .activeCompanies(stats.activeCompanies())
                .inactiveCompanies(stats.inactiveCompanies())
                .companiesCreatedLast30Days(stats.companiesCreatedLast30Days())
                .totalEmployees(stats.totalEmployees())
                .companiesMissingDeviceSerial(stats.companiesMissingDeviceSerial())
                .companiesWithPendingThumbLogs(stats.companiesWithPendingThumbLogs())
                .companiesWithoutAdmin(stats.companiesWithoutAdmin())
                .companiesWithNoPayrollThisMonth(stats.companiesWithNoPayrollThisMonth())
                .pendingThumbLogsTotal(stats.pendingThumbLogsTotal())
                .pendingLeaveRequestsTotal(stats.pendingLeaveRequestsTotal())
                .pendingAdvancesTotal(stats.pendingAdvancesTotal())
                .recentCompanies(recent)
                .build();
    }

    public CompanyDetailResponse createCompany(CreateCompanyRequest request) {
        return companyService.createCompany(request);
    }

    public PageResponse<CompanyResponse> getAllCompanies(int page, int size) {
        return companyService.getAllCompanies(page, size);
    }

    public CompanyDetailResponse getCompanyById(Long id) {
        return companyService.getCompanyById(id);
    }

    public CompanyResponse updateCompany(Long id, UpdateCompanyRequest request) {
        return companyService.updateCompany(id, request);
    }

    public CompanyResponse updateCompanyStatus(Long id, UpdateCompanyStatusRequest request) {
        return companyService.updateCompanyStatus(id, request);
    }

    private HomePlatformCompanyCardResponse toCard(PlatformCompanyCardRow row) {
        return HomePlatformCompanyCardResponse.builder()
                .id(row.id())
                .name(row.name())
                .isActive(row.isActive())
                .totalEmployees(row.totalEmployees())
                .adminName(row.adminName())
                .adminMobile(row.adminMobile())
                .createdAt(row.createdAt())
                .hasSettings(row.hasSettings())
                .hasDeviceSerial(row.hasDeviceSerial())
                .hasAdmin(row.hasAdmin())
                .pendingThumbLogs(row.pendingThumbLogs())
                .payrollRunThisMonth(row.payrollRunThisMonth())
                .build();
    }
}
