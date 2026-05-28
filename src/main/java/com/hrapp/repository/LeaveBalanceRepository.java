package com.hrapp.repository;

import com.hrapp.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByUserIdAndLeaveTypeIdAndYear(Long userId, Long leaveTypeId, Integer year);

    /**
     * Eagerly fetches {@code leaveType} so response DTOs can read its fields
     * without triggering N+1 lookups.
     */
    @Query("SELECT lb FROM LeaveBalance lb " +
            "JOIN FETCH lb.leaveType " +
            "JOIN FETCH lb.user " +
            "WHERE lb.user.id = :userId AND lb.year = :year")
    List<LeaveBalance> findByUserIdAndYear(@Param("userId") Long userId, @Param("year") Integer year);

    @Query("SELECT lb FROM LeaveBalance lb " +
            "JOIN FETCH lb.leaveType " +
            "JOIN FETCH lb.user u " +
            "WHERE u.company.id = :companyId AND lb.year = :year")
    List<LeaveBalance> findByUser_CompanyIdAndYear(@Param("companyId") Long companyId, @Param("year") Integer year);
}
