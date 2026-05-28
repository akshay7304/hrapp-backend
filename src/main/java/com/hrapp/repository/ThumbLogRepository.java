package com.hrapp.repository;

import com.hrapp.entity.ThumbLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ThumbLogRepository extends JpaRepository<ThumbLog, Long> {

    /**
     * Unprocessed logs for a company in arrival order. Backs both the scheduled
     * processor (which drains the queue) and the manual {@code /thumb/process}
     * trigger. Ordering by {@code syncedAt} keeps re-processing deterministic.
     */
    List<ThumbLog> findByCompanyIdAndIsProcessedFalseOrderBySyncedAtAsc(Long companyId);

    /**
     * Unprocessed logs for a single device-side employee id within a company —
     * useful when responding to a per-employee fix-up call from ops.
     */
    List<ThumbLog> findByCompanyIdAndDeviceEmpIdAndIsProcessedFalse(
            Long companyId, String deviceEmpId);

    /** Pending-queue size for dashboards / health checks. */
    Long countByCompanyIdAndIsProcessedFalse(Long companyId);

    /**
     * Counts every punch (processed or not) for a given device employee in the
     * given time window. The thumb-processor uses this with today's start/end
     * to decide whether the next punch is an IN (even count so far) or an OUT
     * (odd count so far) when the device didn't supply a direction.
     */
    long countByCompanyIdAndDeviceEmpIdAndPunchTimeBetween(
            Long companyId, String deviceEmpId, LocalDateTime from, LocalDateTime to);
}
