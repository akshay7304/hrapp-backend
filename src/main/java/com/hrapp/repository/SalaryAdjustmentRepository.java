package com.hrapp.repository;

import com.hrapp.entity.SalaryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryAdjustmentRepository extends JpaRepository<SalaryAdjustment, Long> {

    @Query("SELECT sa FROM SalaryAdjustment sa " +
            "JOIN FETCH sa.user " +
            "JOIN FETCH sa.createdBy " +
            "WHERE sa.user.id = :userId AND sa.month = :month AND sa.year = :year " +
            "ORDER BY sa.createdAt ASC")
    List<SalaryAdjustment> findByUserIdAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    @Query("SELECT sa FROM SalaryAdjustment sa " +
            "JOIN FETCH sa.user " +
            "JOIN FETCH sa.createdBy " +
            "WHERE sa.company.id = :companyId AND sa.month = :month AND sa.year = :year " +
            "ORDER BY sa.createdAt ASC")
    List<SalaryAdjustment> findByCompanyIdAndMonthAndYear(
            @Param("companyId") Long companyId,
            @Param("month") Integer month,
            @Param("year") Integer year);
}
