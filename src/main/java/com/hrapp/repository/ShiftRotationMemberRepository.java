package com.hrapp.repository;

import com.hrapp.entity.ShiftRotationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShiftRotationMemberRepository extends JpaRepository<ShiftRotationMember, Long> {

    List<ShiftRotationMember> findByRotationIdOrderByJoinedAtAsc(Long rotationId);

    Optional<ShiftRotationMember> findByRotationIdAndUserId(Long rotationId, Long userId);

    Optional<ShiftRotationMember> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    long countByRotationId(Long rotationId);

    void deleteByRotationIdAndUserId(Long rotationId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftRotationMember m WHERE m.id = :id")
    void deleteMemberById(@Param("id") Long id);
}
