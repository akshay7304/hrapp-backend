package com.hrapp.repository;

import com.hrapp.entity.ShiftRotationDraft;
import com.hrapp.enums.RotationDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShiftRotationDraftRepository extends JpaRepository<ShiftRotationDraft, Long> {

    Optional<ShiftRotationDraft> findByRotationIdAndStatus(Long rotationId, RotationDraftStatus status);

    Optional<ShiftRotationDraft> findFirstByRotationIdAndStatusOrderByPeriodEndDesc(
            Long rotationId, RotationDraftStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftRotationDraft d WHERE d.id = :id")
    void deleteDraftById(@Param("id") Long id);
}
