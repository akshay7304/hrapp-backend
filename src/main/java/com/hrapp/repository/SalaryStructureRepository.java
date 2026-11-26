package com.hrapp.repository;

import com.hrapp.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

    @Query("SELECT s FROM SalaryStructure s JOIN FETCH s.user "
            + "WHERE s.user.id = :userId ORDER BY s.effectiveFrom DESC, s.id DESC")
    List<SalaryStructure> findByUserIdOrderByEffectiveFromDesc(@Param("userId") Long userId);

    /**
     * Resolves the salary structure that was in force on (or before) the given
     * date — i.e. the latest record whose {@code effectiveFrom <= date}.
     * Used by payroll to snapshot the structure for a given month.
     */
    Optional<SalaryStructure> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long userId, LocalDate date);
}
