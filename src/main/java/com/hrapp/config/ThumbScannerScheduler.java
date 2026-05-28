package com.hrapp.config;

import com.hrapp.dto.response.ThumbProcessResult;
import com.hrapp.entity.Company;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.service.ThumbScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic batch job that drains every active company's unprocessed
 * {@code thumb_logs} queue into {@code attendance}. Runs every 15 minutes
 * by default; the same work can be triggered on-demand by
 * {@code POST /thumb/process} for an individual company.
 * <p>
 * Failures inside a single company are caught and logged so a bad row from
 * one tenant doesn't stop the rest of the fleet from being processed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThumbScannerScheduler {

    private static final long FIFTEEN_MINUTES_MS = 15L * 60L * 1000L;

    private final ThumbScannerService thumbScannerService;
    private final CompanyRepository companyRepository;

    @Scheduled(fixedDelay = FIFTEEN_MINUTES_MS)
    public void processAllCompanies() {
        List<Company> activeCompanies = companyRepository.findByIsActive(true);
        if (activeCompanies.isEmpty()) {
            return;
        }

        log.info("Thumb scheduler tick — companies={}", activeCompanies.size());
        int succeeded = 0;
        int failed = 0;
        for (Company company : activeCompanies) {
            try {
                ThumbProcessResult result = thumbScannerService.processThumbLogs(company.getId());
                log.info("Thumb processing for company={} success={} skipped={} error={}",
                        company.getId(), result.getSuccessCount(),
                        result.getSkippedCount(), result.getErrorCount());
                succeeded++;
            } catch (Exception ex) {
                failed++;
                log.error("Thumb processing failed for companyId={}", company.getId(), ex);
            }
        }
        log.info("Thumb scheduler done — succeeded={} failed={}", succeeded, failed);
    }
}
