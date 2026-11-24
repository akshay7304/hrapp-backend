package com.hrapp.repository;

import com.hrapp.entity.ShiftRotationDraft;
import com.hrapp.enums.RotationDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShiftRotationDraftRepository extends JpaRepository<ShiftRotationDraft, Long> {

    Optional<ShiftRotationDraft> findByRotationIdAndStatus(Long rotationId, RotationDraftStatus status);

    Optional<ShiftRotationDraft> findFirstByRotationIdAndStatusOrderByPeriodEndDesc(
            Long rotationId, RotationDraftStatus status);
}
