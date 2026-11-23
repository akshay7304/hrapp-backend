package com.hrapp.repository;

import com.hrapp.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @EntityGraph(attributePaths = {"shift", "user"})
    Optional<ShiftAssignment> findFirstByUser_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long userId, LocalDate effectiveFrom);

    @Query("SELECT sa FROM ShiftAssignment sa "
            + "JOIN FETCH sa.shift "
            + "JOIN FETCH sa.user "
            + "LEFT JOIN FETCH sa.createdBy "
            + "WHERE sa.user.id = :userId "
            + "ORDER BY sa.effectiveFrom DESC")
    List<ShiftAssignment> findHistoryByUserId(@Param("userId") Long userId);

    Optional<ShiftAssignment> findByUser_IdAndEffectiveFrom(Long userId, LocalDate effectiveFrom);

    long countByShiftId(Long shiftId);
}
