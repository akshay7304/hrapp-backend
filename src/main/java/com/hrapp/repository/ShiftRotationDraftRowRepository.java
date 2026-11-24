package com.hrapp.repository;

import com.hrapp.entity.ShiftRotationDraftRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShiftRotationDraftRowRepository extends JpaRepository<ShiftRotationDraftRow, Long> {

    List<ShiftRotationDraftRow> findByDraftIdOrderByUser_FullNameAsc(Long draftId);

    Optional<ShiftRotationDraftRow> findByDraftIdAndUserId(Long draftId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftRotationDraftRow r WHERE r.draft.id = :draftId")
    void deleteByDraftId(@Param("draftId") Long draftId);
}
