package com.hrapp.repository;

import com.hrapp.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByCompanyIdOrderByNameAsc(Long companyId);

    List<Shift> findByCompanyIdAndIsActiveTrueOrderByNameAsc(Long companyId);

    Optional<Shift> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    @Query("SELECT s FROM Shift s JOIN FETCH s.company "
            + "WHERE s.company.id = :companyId AND s.isDefault = true AND s.isActive = true")
    Optional<Shift> findByCompanyIdAndIsDefaultTrueAndIsActiveTrue(@Param("companyId") Long companyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Shift s SET s.isDefault = false "
            + "WHERE s.company.id = :companyId AND s.isDefault = true "
            + "AND (:excludeId IS NULL OR s.id <> :excludeId)")
    int clearDefaultForCompany(
            @Param("companyId") Long companyId,
            @Param("excludeId") Long excludeId);
}
