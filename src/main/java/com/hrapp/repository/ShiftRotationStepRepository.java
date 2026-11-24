package com.hrapp.repository;

import com.hrapp.entity.ShiftRotationStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShiftRotationStepRepository extends JpaRepository<ShiftRotationStep, Long> {

    List<ShiftRotationStep> findByRotationIdOrderByStepOrderAsc(Long rotationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftRotationStep s WHERE s.rotation.id = :rotationId")
    void deleteByRotationId(@Param("rotationId") Long rotationId);
}
