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

    /**
     * Single round-trip for JWT request gate: user + company + status
     * (includes {@code tokensRevokedBefore} on the user row).
     */
    @Query("SELECT u FROM User u "
            + "LEFT JOIN FETCH u.company "
            + "LEFT JOIN FETCH u.status "
            + "WHERE u.id = :userId")
    Optional<User> findAccessSnapshotById(@Param("userId") Long userId);

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

    long countByDepartmentId(Long departmentId);

    long countByDesignationId(Long designationId);

    /** Active head-count for ops dashboard (status.name typically {@code "Active"}). */
    long countByCompanyIdAndStatus_Name(Long companyId, String statusName);

    /**
     * Resolves a biometric-device-side employee id to a real user inside the
     * given company. Used by the thumb-scanner integration to translate a raw
     * punch into an {@link User} reference.
     */
    Optional<User> findByThumbDeviceIdAndCompanyId(String thumbDeviceId, Long companyId);

    /**
     * Global lookup by biometric enrol id (no company filter). Used at the
     * device-push entry point where the caller doesn't yet know which tenant
     * the device belongs to — the matched user's {@code companyId} is what
     * tells us. Assumes biometric enrol ids do not collide across companies
     * sharing the same device fleet; the typical deployment satisfies this
     * because each company has its own physical device.
     */
    Optional<User> findByThumbDeviceId(String thumbDeviceId);

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
