package com.hrapp.repository;

import com.hrapp.entity.SalaryPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {

    @Query("SELECT sp FROM SalaryPayment sp " +
            "JOIN FETCH sp.user u " +
            "JOIN FETCH sp.salaryStructure " +
            "LEFT JOIN FETCH sp.generatedBy " +
            "WHERE u.id = :userId AND sp.month = :month AND sp.year = :year")
    Optional<SalaryPayment> findByUserIdAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    /**
     * LEFT JOIN FETCH on {@code department} and {@code designation} is added so
     * the reports layer can render those names without triggering N+1 lookups;
     * they're single-valued associations so they don't multiply result rows.
     */
    @Query("SELECT sp FROM SalaryPayment sp " +
            "JOIN FETCH sp.user u " +
            "LEFT JOIN FETCH u.department " +
            "LEFT JOIN FETCH u.designation " +
            "JOIN FETCH sp.salaryStructure " +
            "LEFT JOIN FETCH sp.generatedBy " +
            "WHERE u.company.id = :companyId AND sp.month = :month AND sp.year = :year " +
            "ORDER BY u.fullName ASC")
    List<SalaryPayment> findByUser_CompanyIdAndMonthAndYear(
            @Param("companyId") Long companyId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    /**
     * Paginated company-scoped payroll lookup. {@link EntityGraph} eagerly
     * loads {@code user} (with its {@code department}/{@code designation}),
     * {@code salaryStructure} and the optional {@code generatedBy} — same
     * shape as the non-paginated query above, but safe with {@code Pageable}.
     */
    @EntityGraph(attributePaths = {
            "user", "user.department", "user.designation",
            "salaryStructure", "generatedBy"})
    Page<SalaryPayment> findByUser_CompanyIdAndMonthAndYear(
            Long companyId, Integer month, Integer year, Pageable pageable);

    boolean existsByUserIdAndMonthAndYear(Long userId, Integer month, Integer year);
}
