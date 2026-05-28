package com.hrapp.repository;

import com.hrapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMobile(String mobile);

    boolean existsByMobile(String mobile);

    boolean existsByEmpCodeAndCompanyId(String empCode, Long companyId);

    /**
     * Fetches all users of a company eagerly joining the lookup relations
     * (department, designation, status, company) — avoids N+1 when mapping
     * a list of users to response DTOs.
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.company " +
            "LEFT JOIN FETCH u.department " +
            "LEFT JOIN FETCH u.designation " +
            "LEFT JOIN FETCH u.status " +
            "WHERE u.company.id = :companyId")
    List<User> findByCompanyId(@Param("companyId") Long companyId);

    /**
     * Fetches all employees of a company whose {@code status.name} matches
     * (typically {@code "Active"}). Used by payroll to scope a payroll run
     * to the currently-employed workforce.
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.company " +
            "JOIN FETCH u.status s " +
            "WHERE u.company.id = :companyId AND s.name = :statusName")
    List<User> findByCompanyIdAndStatusName(
            @Param("companyId") Long companyId,
            @Param("statusName") String statusName);

    /**
     * Fetches employees of a company whose {@code status.name} is NOT in the
     * given list. Used by payroll to include everyone except clearly off-roll
     * statuses (e.g. {@code "Terminated"}, {@code "Resigned"}) so that
     * Probation / Inactive / Suspended / On Notice Period / Contract employees
     * still receive their payslips.
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.company " +
            "JOIN FETCH u.status s " +
            "WHERE u.company.id = :companyId AND s.name NOT IN :excludedStatuses")
    List<User> findByCompanyIdAndStatus_NameNotIn(
            @Param("companyId") Long companyId,
            @Param("excludedStatuses") List<String> excludedStatuses);

    /** Total head-count for a company (any status). Used by the company report list. */
    long countByCompanyId(Long companyId);

    /**
     * Returns users in a given company that hold the supplied role name.
     * Used by the company-detail endpoint to surface the admin contact;
     * typically returns at most one row but tolerant of multiple admins.
     */
    @Query("SELECT u FROM User u " +
            "JOIN UserRole ur ON ur.user = u " +
            "WHERE u.company.id = :companyId AND ur.role.name = :roleName")
    List<User> findByCompanyIdAndRoleName(
            @Param("companyId") Long companyId,
            @Param("roleName") String roleName);
}
