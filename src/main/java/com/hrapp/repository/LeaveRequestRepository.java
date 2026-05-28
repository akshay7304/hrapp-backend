package com.hrapp.repository;

import com.hrapp.entity.LeaveRequest;
import com.hrapp.enums.LeaveRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.user " +
            "JOIN FETCH lr.leaveType " +
            "LEFT JOIN FETCH lr.actionedBy " +
            "WHERE lr.user.id = :userId AND lr.status = :status")
    List<LeaveRequest> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") LeaveRequestStatus status);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.user " +
            "JOIN FETCH lr.leaveType " +
            "LEFT JOIN FETCH lr.actionedBy " +
            "WHERE lr.user.id = :userId AND lr.fromDate BETWEEN :from AND :to")
    List<LeaveRequest> findByUserIdAndFromDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Paginated variant of the per-user date-range lookup. Uses
     * {@link EntityGraph} so {@code user}, {@code leaveType} and the optional
     * {@code actionedBy} all load eagerly without the {@code JOIN FETCH +
     * Pageable} in-memory-pagination pitfall.
     */
    @EntityGraph(attributePaths = {"user", "leaveType", "actionedBy"})
    Page<LeaveRequest> findByUserIdAndFromDateBetween(
            Long userId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.user u " +
            "JOIN FETCH lr.leaveType " +
            "LEFT JOIN FETCH lr.actionedBy " +
            "WHERE u.company.id = :companyId AND lr.status = :status")
    List<LeaveRequest> findByUser_CompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") LeaveRequestStatus status);

    /** Paginated company-scoped lookup by status. Used by the pending-leaves admin view. */
    @EntityGraph(attributePaths = {"user", "leaveType", "actionedBy"})
    Page<LeaveRequest> findByUser_CompanyIdAndStatus(
            Long companyId, LeaveRequestStatus status, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.user u " +
            "JOIN FETCH lr.leaveType " +
            "LEFT JOIN FETCH lr.actionedBy " +
            "WHERE u.company.id = :companyId AND lr.fromDate BETWEEN :from AND :to")
    List<LeaveRequest> findByUser_CompanyIdAndFromDateBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Paginated company-scoped lookup by date range. Used by the all-leaves admin view. */
    @EntityGraph(attributePaths = {"user", "leaveType", "actionedBy"})
    Page<LeaveRequest> findByUser_CompanyIdAndFromDateBetween(
            Long companyId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    /**
     * Returns true when the user already has a leave request whose date range
     * overlaps the supplied range. Overlap rule:
     * {@code existing.fromDate <= newToDate AND existing.toDate >= newFromDate}.
     * Used to block double-booking when applying for a new leave.
     */
    boolean existsByUserIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            Long userId,
            List<LeaveRequestStatus> statuses,
            LocalDate toDate,
            LocalDate fromDate);

    /**
     * Returns all leave requests of the given status whose range overlaps the
     * supplied {@code [from, to]} window. Used by payroll to classify
     * {@code ON_LEAVE} attendance days as paid or unpaid based on
     * {@code leaveType.isPaid}.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.leaveType " +
            "WHERE lr.user.id = :userId AND lr.status = :status " +
            "AND lr.fromDate <= :to AND lr.toDate >= :from")
    List<LeaveRequest> findOverlappingApprovedLeaves(
            @Param("userId") Long userId,
            @Param("status") LeaveRequestStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
