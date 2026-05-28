package com.hrapp.config;

import com.hrapp.entity.Company;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs once a year at 01:00 AM on January 1st to roll forward leave balances
 * for every active company. The cron uses Spring's 6-field format:
 * {@code second minute hour day-of-month month day-of-week} → fires only in
 * January (month = 1), matching the spec's "1 am on Jan 1 every year".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeaveScheduler {

    private final LeaveService leaveService;
    private final CompanyRepository companyRepository;

    @Scheduled(cron = "0 0 1 1 1 *")
    public void runYearEndCarryForward() {
        int previousYear = LocalDate.now().getYear() - 1;
        log.info("Year-end carry-forward starting for year={}", previousYear);

        List<Company> activeCompanies = companyRepository.findByIsActive(true);
        int succeeded = 0;
        int failed = 0;
        for (Company company : activeCompanies) {
            try {
                leaveService.processYearEndCarryForward(company.getId(), previousYear);
                succeeded++;
            } catch (Exception ex) {
                failed++;
                log.error("Year-end carry-forward failed for companyId={}", company.getId(), ex);
            }
        }
        log.info("Year-end carry-forward complete — total={} succeeded={} failed={}",
                activeCompanies.size(), succeeded, failed);
    }
}
