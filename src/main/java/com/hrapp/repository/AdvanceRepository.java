package com.hrapp.repository;

import com.hrapp.entity.Advance;
import com.hrapp.enums.AdvanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvanceRepository extends JpaRepository<Advance, Long> {

    /**
     * Returns approved advances scheduled to recover from the given month
     * which have not yet been recovered. Used by payroll to compute the
     * {@code advanceDeduction} component of net salary.
     */
    List<Advance> findByUserIdAndDeductFromMonthAndDeductFromYearAndStatusAndIsRecovered(
            Long userId,
            Integer deductFromMonth,
            Integer deductFromYear,
            AdvanceStatus status,
            Boolean isRecovered);

    /**
     * Employee-side history feed — newest request first. JOIN FETCH on user
     * and approvedBy so response building doesn't trigger lazy loads.
     */
    @Query("SELECT a FROM Advance a " +
            "JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.approvedBy " +
            "WHERE a.user.id = :userId " +
            "ORDER BY a.createdAt DESC")
    List<Advance> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /** Used by the request-advance pre-check to block duplicate PENDING requests. */
    @Query("SELECT a FROM Advance a " +
            "JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.approvedBy " +
            "WHERE a.user.id = :userId AND a.status = :status")
    List<Advance> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") AdvanceStatus status);

    /** Admin/HR pending queue — oldest first so first-come-first-served. */
    @Query("SELECT a FROM Advance a " +
            "JOIN FETCH a.user u " +
            "LEFT JOIN FETCH a.approvedBy " +
            "WHERE u.company.id = :companyId AND a.status = :status " +
            "ORDER BY a.createdAt ASC")
    List<Advance> findByUser_CompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") AdvanceStatus status);

    /** Paginated variant for the admin pending queue. */
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<Advance> findByUser_CompanyIdAndStatus(
            Long companyId, AdvanceStatus status, Pageable pageable);

    /** Full company history — newest first. */
    @Query("SELECT a FROM Advance a " +
            "JOIN FETCH a.user u " +
            "LEFT JOIN FETCH a.approvedBy " +
            "WHERE u.company.id = :companyId " +
            "ORDER BY a.createdAt DESC")
    List<Advance> findByUser_CompanyIdOrderByCreatedAtDesc(@Param("companyId") Long companyId);

    /** Paginated company history. Default order is encoded in the method name. */
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<Advance> findByUser_CompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
}
