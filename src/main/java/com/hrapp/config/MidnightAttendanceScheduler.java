package com.hrapp.config;

import com.hrapp.entity.Company;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs at 11:59 PM every day to auto-close attendance rows where employees
 * checked in but never checked out.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MidnightAttendanceScheduler {

    private final AttendanceService attendanceService;
    private final CompanyRepository companyRepository;

    @Scheduled(cron = "0 59 23 * * *")
    public void runAutoCheckout() {
        log.info("Midnight auto-checkout job starting");

        List<Company> activeCompanies = companyRepository.findByIsActive(true);
        for (Company company : activeCompanies) {
            try {
                int count = attendanceService.autoCheckoutMissed(company.getId());
                log.info("Auto checkout processed {} records for company {}", count, company.getId());
            } catch (Exception ex) {
                log.error("Auto checkout failed for companyId={}", company.getId(), ex);
            }
        }

        log.info("Midnight auto-checkout job complete — companies={}", activeCompanies.size());
    }
}
