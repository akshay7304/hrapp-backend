package com.hrapp.repository;

import com.hrapp.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * Candidates for effective assignment on {@code onDate} for many users —
     * caller picks the latest {@code effectiveFrom} per user (list is ordered DESC).
     */
    @EntityGraph(attributePaths = {"shift", "user"})
    @Query("SELECT sa FROM ShiftAssignment sa "
            + "WHERE sa.user.id IN :userIds "
            + "AND sa.effectiveFrom <= :onDate "
            + "ORDER BY sa.user.id ASC, sa.effectiveFrom DESC")
    List<ShiftAssignment> findEffectiveOnOrBeforeForUsers(
            @Param("userIds") Collection<Long> userIds,
            @Param("onDate") LocalDate onDate);

    long countByShiftId(Long shiftId);

    /**
     * Removes later-dated assignments after {@code after} with no upper bound
     * (manual open-ended assign wins over all future rows).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftAssignment sa "
            + "WHERE sa.user.id = :userId AND sa.effectiveFrom > :after")
    int deleteByUserIdAndEffectiveFromAfter(
            @Param("userId") Long userId,
            @Param("after") LocalDate after);

    /**
     * Removes later-dated assignments inside a cover window
     * ({@code after} exclusive … {@code through} inclusive). Used by rotation confirm
     * so assigns after the draft period end are preserved.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftAssignment sa "
            + "WHERE sa.user.id = :userId "
            + "AND sa.effectiveFrom > :after "
            + "AND sa.effectiveFrom <= :through")
    int deleteByUserIdAndEffectiveFromAfterAndThrough(
            @Param("userId") Long userId,
            @Param("after") LocalDate after,
            @Param("through") LocalDate through);
}
